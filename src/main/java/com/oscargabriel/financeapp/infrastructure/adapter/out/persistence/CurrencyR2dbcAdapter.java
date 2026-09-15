package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;

import reactor.core.publisher.Mono;

@Component
public class CurrencyR2dbcAdapter implements CurrencyQueryPort {

    private static final String EXISTE = """
            SELECT EXISTS (
                SELECT 1
                  FROM finance.currencies
                 WHERE code = :code
                   AND is_active
            )
            """;

    private final DatabaseClient databaseClient;

    public CurrencyR2dbcAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Boolean> exists(String code) {
        return databaseClient.sql(EXISTE)
                .bind("code", code)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one();
    }
}
