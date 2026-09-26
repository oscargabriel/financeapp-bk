package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.UUID;

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
public class AccountR2dbcAdapter implements AccountQueryPort, AccountRepositoryPort {

    private static final String SQL = """
            SELECT id,
                   name,
                   type,
                   currency_code,
                   current_balance,
                   credit_limit,
                   is_active
              FROM finance.accounts
             WHERE user_id = :userId
               AND deleted_at IS NULL
               AND (is_active OR :includeInactive)
             ORDER BY is_active DESC, lower(name)
            """;

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
            RETURNING id, name, type, currency_code, current_balance, credit_limit, is_active
            """;

    private final DatabaseClient databaseClient;

    public AccountR2dbcAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

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

        sentencia = account.creditLimit() == null
                ? sentencia.bindNull("creditLimit", BigDecimal.class)
                : sentencia.bind("creditLimit", account.creditLimit());
        sentencia = account.statementDay() == null
                ? sentencia.bindNull("statementDay", Short.class)
                : sentencia.bind("statementDay", account.statementDay().shortValue());
        sentencia = account.paymentDueDay() == null
                ? sentencia.bindNull("paymentDueDay", Short.class)
                : sentencia.bind("paymentDueDay", account.paymentDueDay().shortValue());

        return sentencia.map((row, metadata) -> toDomain(row))
                .one()
                .onErrorMap(DuplicateKeyException.class, AccountR2dbcAdapter::comoConflicto);
    }

    private static Account toDomain(Row row) {
        return new Account(
                row.get("id", UUID.class),
                row.get("name", String.class),
                AccountType.valueOf(row.get("type", String.class)),
                row.get("currency_code", String.class),
                row.get("current_balance", BigDecimal.class),
                row.get("credit_limit", BigDecimal.class),
                Boolean.TRUE.equals(row.get("is_active", Boolean.class)));
    }

    /**
     * El unico indice unico de la tabla ademas de la PK es ux_accounts_user_name, y la PK es un v7 recien
     * generado: un duplicado aqui es un nombre que ya usa otra cuenta no borrada del usuario.
     */
    private static BadRequestException comoConflicto(DuplicateKeyException e) {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "Ya hay una cuenta con ese nombre", "name", e);
    }
}
