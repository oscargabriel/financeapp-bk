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

    /**
     * current_balance incluye lo programado: el trigger aplica todo movimiento confirmado sin mirar la
     * fecha. El saldo vigente le resta lo que todavia no ha ocurrido (FA-106), y es el que sale de aqui.
     * committed_credit es el capital de las cuotas que todavia no llegan: ya ocupa cupo (FA-108).
     */
    private static final String COLUMNAS = """
            id, name, type, currency_code, initial_balance,
                   current_balance - finance.scheduled_balance_delta(id) AS current_balance,
                   credit_limit, statement_day, payment_due_day, monthly_interest_rate, is_active,
                   finance.committed_credit(id) AS committed_credit""";

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
                    credit_limit, statement_day, payment_due_day, monthly_interest_rate)
            VALUES (:id, :userId, :name, :type, :currencyCode, :initialBalance,
                    :creditLimit, :statementDay, :paymentDueDay, :monthlyInterestRate)
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
                   payment_due_day = :paymentDueDay,
                   monthly_interest_rate = :monthlyInterestRate,
                   is_active = :isActive
             WHERE id = :id
               AND user_id = :userId
               AND deleted_at IS NULL
            RETURNING %s
            """.formatted(COLUMNAS);

    /**
     * La fila se queda: los confirmados la siguen referenciando por la FK. Sus pendientes se van en la
     * misma sentencia (FA-97): PostgreSQL ejecuta el DELETE del CTE aunque el SELECT no lo lea, y si la
     * cuenta ya no estaba viva, borrada sale vacia y no se toca ningun pendiente. Borrar un pendiente
     * no mueve saldos: trg_transactions_sync_balance solo revierte filas CONFIRMED.
     */
    private static final String BORRAR = """
            WITH borrada AS (
                UPDATE finance.accounts
                   SET deleted_at = now()
                 WHERE id = :id
                   AND user_id = :userId
                   AND deleted_at IS NULL
                RETURNING id
            ), pendientes AS (
                DELETE FROM finance.transactions t
                 USING borrada b
                 WHERE t.user_id = :userId
                   AND t.status = 'PENDING'
                   AND (t.account_id = b.id OR t.destination_account_id = b.id)
            )
            SELECT count(*) AS borradas FROM borrada
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

        return camposDeCredito(sentencia, account.creditLimit(), account.statementDay(), account.paymentDueDay(),
                        account.monthlyInterestRate())
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
                .bind("initialBalance", account.initialBalance())
                .bind("isActive", account.active());

        return camposDeCredito(sentencia, account.creditLimit(), account.statementDay(), account.paymentDueDay(),
                        account.monthlyInterestRate())
                .map((row, metadata) -> toDomain(row))
                .one()
                .onErrorMap(DuplicateKeyException.class, AccountR2dbcAdapter::comoConflicto);
    }

    @Override
    public Mono<Boolean> softDelete(UUID accountId, UUID userId) {
        return databaseClient.sql(BORRAR)
                .bind("id", accountId)
                .bind("userId", userId)
                .map((row, metadata) -> row.get("borradas", Long.class) > 0)
                .one();
    }

    /** Los cuatro son null fuera de una CREDIT, y R2DBC exige el tipo para enlazar un null. */
    private static DatabaseClient.GenericExecuteSpec camposDeCredito(DatabaseClient.GenericExecuteSpec sentencia,
            BigDecimal creditLimit, Integer statementDay, Integer paymentDueDay, BigDecimal monthlyInterestRate) {
        sentencia = creditLimit == null
                ? sentencia.bindNull("creditLimit", BigDecimal.class)
                : sentencia.bind("creditLimit", creditLimit);
        sentencia = statementDay == null
                ? sentencia.bindNull("statementDay", Short.class)
                : sentencia.bind("statementDay", statementDay.shortValue());
        sentencia = paymentDueDay == null
                ? sentencia.bindNull("paymentDueDay", Short.class)
                : sentencia.bind("paymentDueDay", paymentDueDay.shortValue());
        return monthlyInterestRate == null
                ? sentencia.bindNull("monthlyInterestRate", BigDecimal.class)
                : sentencia.bind("monthlyInterestRate", monthlyInterestRate);
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
                row.get("monthly_interest_rate", BigDecimal.class),
                Boolean.TRUE.equals(row.get("is_active", Boolean.class)),
                row.get("committed_credit", BigDecimal.class));
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
