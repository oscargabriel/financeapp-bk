package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import lombok.AllArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.port.out.RegistrationAllowlistPort;

import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class RegistrationAllowlistR2dbcAdapter implements RegistrationAllowlistPort {

    /** El dominio se compara exacto: '@bruno.local' no admite 'otro.bruno.local'. */
    private static final String ADMITIDO = """
            SELECT EXISTS (
                SELECT 1
                  FROM finance.registration_allowlist
                 WHERE entry IN (:email, '@' || split_part(:email, '@', 2))
            )
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Boolean> isAllowed(String email) {
        return databaseClient.sql(ADMITIDO)
                .bind("email", email)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one();
    }
}
