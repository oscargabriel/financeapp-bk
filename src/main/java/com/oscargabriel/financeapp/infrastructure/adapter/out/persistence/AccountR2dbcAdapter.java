package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.NewAccount;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.AccountRepositoryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class AccountR2dbcAdapter implements AccountQueryPort, AccountRepositoryPort {

    private static final String COLUMNAS = """
            id, name, type, currency_code, initial_balance, current_balance,
                   credit_limit, statement_day, payment_due_day, is_active""";

    private static final String SQL = """
            SELECT %s
              FROM finance.accounts
             WHERE user_id = :userId
               AND deleted_at IS NULL
               AND (is_active OR :includeInactive)
             ORDER BY is_active DESC, lower(name)
            """.formatted(COLUMNAS);

    /**
     * current_balance no esta en la lista de columnas: lo siembra trg_accounts_seed_balance a partir
     * de initial_balance, y el RETURNING devuelve la fila despues de ese trigger.
     */
    private static final String INSERTAR = """
            INSERT INTO finance.accounts
                   (id, user_id, name, type, currency_code, initial_balance,
                    credit_limit, statement_day, payment_due_day)
            VALUES (:id, :userId, :name, :type, :currencyCode, :initialBalance,
                    :creditLimit, :statementDay, :paymentDueDay)
            RETURNING %s
            """.formatted(COLUMNAS);

    private static final String POR_ID = """
            SELECT %s
              FROM finance.accounts
             WHERE id = :id
               AND user_id = :userId
               AND deleted_at IS NULL
            """.formatted(COLUMNAS);

    private static final String TIENE_MOVIMIENTOS = """
            SELECT EXISTS (SELECT 1
                             FROM finance.transactions
                            WHERE account_id = :accountId
                               OR destination_account_id = :accountId) AS tiene
            """;

    /**
     * current_balance tampoco se escribe aqui: si cambia initial_balance, trg_accounts_shift_balance lo
     * corre en la misma diferencia. Mismo filtro que POR_ID: borrada entre la lectura y la escritura,
     * el RETURNING sale vacio.
     */
    private static final String ACTUALIZAR = """
            UPDATE finance.accounts
               SET name = :name,
                   currency_code = :currencyCode,
                   initial_balance = :initialBalance,
                   credit_limit = :creditLimit,
                   statement_day = :statementDay,
                   payment_due_day = :paymentDueDay
             WHERE id = :id
               AND user_id = :userId
               AND deleted_at IS NULL
            RETURNING %s
            """.formatted(COLUMNAS);

    /** La fila se queda: los movimientos la siguen referenciando por la FK. */
    private static final String BORRAR = """
            UPDATE finance.accounts
               SET deleted_at = now()
             WHERE id = :id
               AND user_id = :userId
               AND deleted_at IS NULL
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Flux<Account> findByUser(UUID userId, boolean includeInactive) {
        return databaseClient.sql(SQL)
                .bind("userId", userId)
                .bind("includeInactive", includeInactive)
                .map((row, metadata) -> toDomain(row))
                .all();
    }

    @Override
    public Mono<Account> create(NewAccount account) {
        DatabaseClient.GenericExecuteSpec sentencia = databaseClient.sql(INSERTAR)
                .bind("id", account.id())
                .bind("userId", account.userId())
                .bind("name", account.name())
                .bind("type", account.type().name())
                .bind("currencyCode", account.currencyCode())
                .bind("initialBalance", account.initialBalance());

        return camposDeCredito(sentencia, account.creditLimit(), account.statementDay(), account.paymentDueDay())
                .map((row, metadata) -> toDomain(row))
                .one()
                .onErrorMap(DuplicateKeyException.class, AccountR2dbcAdapter::comoConflicto);
    }

    @Override
    public Mono<Account> findActiveByIdAndUser(UUID accountId, UUID userId) {
        return databaseClient.sql(POR_ID)
                .bind("id", accountId)
                .bind("userId", userId)
                .map((row, metadata) -> toDomain(row))
                .one();
    }

    @Override
    public Mono<Boolean> hasTransactions(UUID accountId) {
        return databaseClient.sql(TIENE_MOVIMIENTOS)
                .bind("accountId", accountId)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("tiene", Boolean.class)))
                .one();
    }

    @Override
    public Mono<Account> update(UUID userId, Account account) {
        DatabaseClient.GenericExecuteSpec sentencia = databaseClient.sql(ACTUALIZAR)
                .bind("id", account.id())
                .bind("userId", userId)
                .bind("name", account.name())
                .bind("currencyCode", account.currencyCode())
                .bind("initialBalance", account.initialBalance());

        return camposDeCredito(sentencia, account.creditLimit(), account.statementDay(), account.paymentDueDay())
                .map((row, metadata) -> toDomain(row))
                .one()
                .onErrorMap(DuplicateKeyException.class, AccountR2dbcAdapter::comoConflicto);
    }

    @Override
    public Mono<Boolean> softDelete(UUID accountId, UUID userId) {
        return databaseClient.sql(BORRAR)
                .bind("id", accountId)
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .map(filas -> filas > 0);
    }

    /** Los tres son null fuera de una CREDIT, y R2DBC exige el tipo para enlazar un null. */
    private static DatabaseClient.GenericExecuteSpec camposDeCredito(DatabaseClient.GenericExecuteSpec sentencia,
            BigDecimal creditLimit, Integer statementDay, Integer paymentDueDay) {
        sentencia = creditLimit == null
                ? sentencia.bindNull("creditLimit", BigDecimal.class)
                : sentencia.bind("creditLimit", creditLimit);
        sentencia = statementDay == null
                ? sentencia.bindNull("statementDay", Short.class)
                : sentencia.bind("statementDay", statementDay.shortValue());
        return paymentDueDay == null
                ? sentencia.bindNull("paymentDueDay", Short.class)
                : sentencia.bind("paymentDueDay", paymentDueDay.shortValue());
    }

    private static Account toDomain(Row row) {
        return new Account(
                row.get("id", UUID.class),
                row.get("name", String.class),
                AccountType.valueOf(row.get("type", String.class)),
                row.get("currency_code", String.class),
                row.get("initial_balance", BigDecimal.class),
                row.get("current_balance", BigDecimal.class),
                row.get("credit_limit", BigDecimal.class),
                comoEntero(row.get("statement_day", Short.class)),
                comoEntero(row.get("payment_due_day", Short.class)),
                Boolean.TRUE.equals(row.get("is_active", Boolean.class)));
    }

    private static Integer comoEntero(Short dia) {
        return dia == null ? null : dia.intValue();
    }

    /**
     * El unico indice unico de la tabla ademas de la PK es ux_accounts_user_name, y en el alta la PK es
     * un v7 recien generado: un duplicado aqui es un nombre que ya usa otra cuenta no borrada del usuario.
     */
    private static BadRequestException comoConflicto(DuplicateKeyException e) {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "Ya hay una cuenta con ese nombre", "name", e);
    }
}
