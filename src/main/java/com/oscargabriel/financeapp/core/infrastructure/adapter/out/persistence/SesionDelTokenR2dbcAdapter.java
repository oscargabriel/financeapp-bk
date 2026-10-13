package com.oscargabriel.financeapp.core.infrastructure.adapter.out.persistence;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.compartido.domain.port.out.SesionDelTokenPort;

import reactor.core.publisher.Mono;

/**
 * Corre en cada request Bearer, por eso es una sola lectura por clave primaria. El filtro de
 * usuario activo es el mismo del login: un token no sobrevive a la desactivacion de su dueno.
 */
@Component
@AllArgsConstructor
public class SesionDelTokenR2dbcAdapter implements SesionDelTokenPort {

    private static final String SESION_ABIERTA = """
            SELECT EXISTS (
                SELECT 1
                  FROM finance.users
                 WHERE id = :id
                   AND deleted_at IS NULL
                   AND is_active
                   AND token_version = :version
            )
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Boolean> abierta(UUID userId, long version) {
        return databaseClient.sql(SESION_ABIERTA)
                .bind("id", userId)
                .bind("version", version)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one();
    }
}
