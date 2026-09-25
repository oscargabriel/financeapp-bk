package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;

@Component
public class AccountR2dbcAdapter implements AccountQueryPort {

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
}
