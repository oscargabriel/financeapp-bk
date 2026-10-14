package com.oscargabriel.financeapp.core.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.Affiliation;
import com.oscargabriel.financeapp.core.domain.model.AffiliationDirection;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliationRepositoryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class AffiliationR2dbcAdapter implements AffiliationRepositoryPort {

    private static final String INSERTAR = """
            INSERT INTO finance.affiliations
                   (id, requester_id, addressee_email, addressee_id, status, created_at, updated_at)
            VALUES (:id, :requesterId, :addresseeEmail, :addresseeId, :status, :createdAt, :updatedAt)
            """;

    /** Sin destinatario solo cuenta la enviada al mismo correo: es la unica relacion con esa persona que el usuario ve. */
    private static final String ABIERTA_AL_CORREO = """
            SELECT EXISTS (
                SELECT 1
                  FROM finance.affiliations
                 WHERE requester_id = :userId
                   AND addressee_email = :email
                   AND status = 'REQUESTED'
            )
            """;

    /**
     * Con destinatario, ademas, cualquier abierta entre los dos usuarios en cualquier sentido. La de
     * la otra persona hacia el usuario la ve como recibida, asi que el 409 no le revela nada.
     */
    private static final String ABIERTA_CON_LA_PERSONA = """
            SELECT EXISTS (
                SELECT 1
                  FROM finance.affiliations
                 WHERE (requester_id = :userId AND addressee_email = :email AND status = 'REQUESTED')
                    OR (status IN ('REQUESTED', 'ACCEPTED')
                        AND ((requester_id = :userId AND addressee_id = :addresseeId)
                          OR (requester_id = :addresseeId AND addressee_id = :userId)))
            )
            """;

    /**
     * La relacion vista por :userId. En una enviada que no esta aceptada sale el correo escrito y sin
     * nombre, tenga o no cuenta: mostrarlo le diria al remitente que correos estan registrados. En el
     * resto, el correo y el nombre actuales de la otra persona.
     */
    private static final String VISTA = """
            SELECT a.id, a.status, a.created_at, a.updated_at,
                   a.requester_id = :userId AS enviada,
                   CASE WHEN a.requester_id = :userId AND a.status <> 'ACCEPTED'
                        THEN a.addressee_email ELSE o.email END AS email,
                   CASE WHEN a.requester_id = :userId AND a.status <> 'ACCEPTED'
                        THEN NULL ELSE o.first_name END AS first_name,
                   CASE WHEN a.requester_id = :userId AND a.status <> 'ACCEPTED'
                        THEN NULL ELSE o.last_name END AS last_name
              FROM finance.affiliations a
              LEFT JOIN finance.users o
                ON o.id = CASE WHEN a.requester_id = :userId THEN a.addressee_id ELSE a.requester_id END
             WHERE (a.requester_id = :userId OR a.addressee_id = :userId)
            """;

    private static final String ABIERTAS_DEL_USUARIO = VISTA + """
               AND a.status IN ('REQUESTED', 'ACCEPTED')
             ORDER BY a.created_at DESC, a.id DESC
            """;

    private static final String VISTA_POR_ID = VISTA + """
               AND a.id = :id
            """;

    private static final String DEL_PARTICIPANTE = """
            SELECT id, requester_id, addressee_email, addressee_id, status, created_at, updated_at
              FROM finance.affiliations
             WHERE id = :id
               AND (requester_id = :userId OR addressee_id = :userId)
            """;

    /** updated_at lo pone trg_affiliations_updated_at. */
    private static final String CAMBIAR_ESTADO = """
            UPDATE finance.affiliations
               SET status = :to
             WHERE id = :id
               AND status = :from
            """;

    private static final String ACEPTADAS = """
            SELECT id, CASE WHEN requester_id = :userId THEN addressee_id ELSE requester_id END AS otro
              FROM finance.affiliations
             WHERE status = 'ACCEPTED'
               AND (requester_id = :userId OR addressee_id = :userId)
            """;

    private final DatabaseClient databaseClient;

    /**
     * El caso de uso ya pregunto si habia una abierta, pero entre esa consulta y el INSERT cabe otro
     * envio: ux_affiliations_open_pair o ux_affiliations_open_email lo cortan y aqui sale el mismo 409.
     */
    @Override
    public Mono<Void> save(Affiliation affiliation) {
        GenericExecuteSpec sentencia = databaseClient.sql(INSERTAR)
                .bind("id", affiliation.id())
                .bind("requesterId", affiliation.requesterId())
                .bind("addresseeEmail", affiliation.addresseeEmail())
                .bind("status", affiliation.status().name())
                .bind("createdAt", utc(affiliation.createdAt()))
                .bind("updatedAt", utc(affiliation.updatedAt()));
        sentencia = affiliation.addresseeId() == null
                ? sentencia.bindNull("addresseeId", UUID.class)
                : sentencia.bind("addresseeId", affiliation.addresseeId());
        return sentencia.fetch().rowsUpdated()
                .onErrorMap(DataIntegrityViolationException.class, e -> new BadRequestException(HttpStatus.CONFLICT,
                        ErrorCodes.DUPLICATE_RESOURCE, "Ya tienes una solicitud abierta o una afiliacion con esa persona",
                        "email", e))
                .then();
    }

    @Override
    public Mono<Boolean> existsOpenVisible(UUID userId, String email, UUID addresseeId) {
        GenericExecuteSpec sentencia = addresseeId == null
                ? databaseClient.sql(ABIERTA_AL_CORREO)
                : databaseClient.sql(ABIERTA_CON_LA_PERSONA).bind("addresseeId", addresseeId);
        return sentencia
                .bind("userId", userId)
                .bind("email", email)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one();
    }

    @Override
    public Flux<AffiliationView> findOpenByUser(UUID userId) {
        return databaseClient.sql(ABIERTAS_DEL_USUARIO)
                .bind("userId", userId)
                .map((row, metadata) -> toView(row))
                .all();
    }

    @Override
    public Mono<Affiliation> findForParticipant(UUID id, UUID userId) {
        return databaseClient.sql(DEL_PARTICIPANTE)
                .bind("id", id)
                .bind("userId", userId)
                .map((row, metadata) -> new Affiliation(
                        row.get("id", UUID.class),
                        row.get("requester_id", UUID.class),
                        row.get("addressee_email", String.class),
                        row.get("addressee_id", UUID.class),
                        AffiliationStatus.valueOf(row.get("status", String.class)),
                        instante(row, "created_at"),
                        instante(row, "updated_at")))
                .one();
    }

    @Override
    public Mono<AffiliationView> findViewForParticipant(UUID id, UUID userId) {
        return databaseClient.sql(VISTA_POR_ID)
                .bind("id", id)
                .bind("userId", userId)
                .map((row, metadata) -> toView(row))
                .one();
    }

    @Override
    public Mono<Boolean> changeStatus(UUID id, AffiliationStatus from, AffiliationStatus to) {
        return databaseClient.sql(CAMBIAR_ESTADO)
                .bind("id", id)
                .bind("from", from.name())
                .bind("to", to.name())
                .fetch().rowsUpdated()
                .map(filas -> filas > 0);
    }

    @Override
    public Mono<Map<UUID, UUID>> findAcceptedByUser(UUID userId) {
        return databaseClient.sql(ACEPTADAS)
                .bind("userId", userId)
                .map((row, metadata) -> Map.entry(row.get("id", UUID.class), row.get("otro", UUID.class)))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private static AffiliationView toView(Row row) {
        return new AffiliationView(
                row.get("id", UUID.class),
                AffiliationStatus.valueOf(row.get("status", String.class)),
                Boolean.TRUE.equals(row.get("enviada", Boolean.class)) ? AffiliationDirection.SENT : AffiliationDirection.RECEIVED,
                row.get("email", String.class),
                row.get("first_name", String.class),
                row.get("last_name", String.class),
                instante(row, "created_at"),
                instante(row, "updated_at"));
    }

    private static Instant instante(Row row, String columna) {
        return row.get(columna, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
