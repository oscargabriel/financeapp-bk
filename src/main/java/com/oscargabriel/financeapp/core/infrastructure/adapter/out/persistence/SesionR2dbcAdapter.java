package com.oscargabriel.financeapp.core.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.core.domain.model.NewRefreshToken;
import com.oscargabriel.financeapp.core.domain.model.RotatedSession;
import com.oscargabriel.financeapp.core.domain.port.out.SesionRepositoryPort;

import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class SesionR2dbcAdapter implements SesionRepositoryPort {

    private static final String ABRIR = """
            INSERT INTO finance.refresh_tokens
                   (id, user_id, family_id, token_hash, token_version, expires_at, created_at)
            VALUES (:id, :userId, :familyId, :tokenHash, :tokenVersion, :expiresAt, :createdAt)
            """;

    /**
     * Una sola sentencia: el UPDATE marca el usado solo si todavia sirve, y el INSERT del reemplazo
     * sale de lo que devolvio. Si el UPDATE no toca filas no se inserta nada. Dos refrescos
     * simultaneos con el mismo hash no pueden ganar los dos: el segundo espera el bloqueo de la fila,
     * vuelve a evaluar used_at IS NULL y no toca nada.
     *
     * El usuario se filtra igual que en el login, y la version tiene que coincidir: subirla al
     * cambiar la contrasena es lo que deja sin servir los refresh de antes.
     */
    private static final String ROTAR = """
            WITH usado AS (
                UPDATE finance.refresh_tokens r
                   SET used_at = :ahora
                  FROM finance.users u
                 WHERE r.token_hash = :hashUsado
                   AND r.used_at IS NULL
                   AND r.revoked_at IS NULL
                   AND r.expires_at > :ahora
                   AND u.id = r.user_id
                   AND u.deleted_at IS NULL
                   AND u.is_active
                   AND u.token_version = r.token_version
                RETURNING r.user_id, r.family_id, r.token_version, r.expires_at
            )
            INSERT INTO finance.refresh_tokens
                   (id, user_id, family_id, token_hash, token_version, expires_at, created_at)
            SELECT :idNuevo, user_id, family_id, :hashNuevo, token_version, expires_at, :ahora
              FROM usado
            RETURNING user_id, token_version, expires_at
            """;

    private static final String REVOCAR_SESION = """
            UPDATE finance.refresh_tokens
               SET revoked_at = :ahora
             WHERE family_id = (SELECT family_id FROM finance.refresh_tokens WHERE token_hash = :hash)
               AND revoked_at IS NULL
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> abrir(NewRefreshToken refresh) {
        return databaseClient.sql(ABRIR)
                .bind("id", refresh.id())
                .bind("userId", refresh.userId())
                .bind("familyId", refresh.familyId())
                .bind("tokenHash", refresh.tokenHash())
                .bind("tokenVersion", refresh.tokenVersion())
                .bind("expiresAt", enUtc(refresh.expiresAt()))
                .bind("createdAt", enUtc(refresh.createdAt()))
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<RotatedSession> rotar(String hashUsado, UUID idNuevo, String hashNuevo, Instant ahora) {
        return databaseClient.sql(ROTAR)
                .bind("hashUsado", hashUsado)
                .bind("idNuevo", idNuevo)
                .bind("hashNuevo", hashNuevo)
                .bind("ahora", enUtc(ahora))
                .map((row, metadata) -> new RotatedSession(
                        row.get("user_id", UUID.class),
                        row.get("token_version", Integer.class),
                        row.get("expires_at", OffsetDateTime.class).toInstant()))
                .one();
    }

    @Override
    public Mono<Void> revocarSesionDe(String hash, Instant ahora) {
        return databaseClient.sql(REVOCAR_SESION)
                .bind("hash", hash)
                .bind("ahora", enUtc(ahora))
                .fetch()
                .rowsUpdated()
                .then();
    }

    private static OffsetDateTime enUtc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
