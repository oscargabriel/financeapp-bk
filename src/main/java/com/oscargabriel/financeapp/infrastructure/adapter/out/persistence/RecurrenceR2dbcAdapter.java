package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.oscargabriel.financeapp.domain.model.Frequency;
import com.oscargabriel.financeapp.domain.model.Recurrence;
import com.oscargabriel.financeapp.domain.model.RecurrenceRule;
import com.oscargabriel.financeapp.domain.model.GroupScope;
import com.oscargabriel.financeapp.domain.model.RecurrenceStatus;
import com.oscargabriel.financeapp.domain.model.RecurrenceTemplateChange;
import com.oscargabriel.financeapp.domain.model.RecurrenceView;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Series de FA-107. Las ocurrencias se insertan con TransactionRepositoryPort.saveAll dentro de la
 * transaccion de cada escritura: su propio TransactionalOperator se une a la que ya esta en curso, asi
 * que una serie y sus ocurrencias entran o se deshacen juntas. Los saldos los mueve el trigger de
 * transactions, como con cualquier movimiento.
 */
@Component
public class RecurrenceR2dbcAdapter implements RecurrenceRepositoryPort {

    private static final String COLUMNAS = """
            r.id, r.user_id, r.type, r.account_id, r.category_id, r.amount, r.currency_code, r.description,
                   r.frequency, r.interval_count, r.day_of_week, r.day_of_month, r.start_date, r.end_date,
                   r.occurrence_limit, r.generated_count, r.prior_count, r.status""";

    private static final String INSERTAR = """
            INSERT INTO finance.recurrences
                   (id, user_id, account_id, category_id, type, amount, currency_code, description,
                    frequency, interval_count, day_of_week, day_of_month, start_date, end_date,
                    occurrence_limit, generated_count, prior_count, status)
            VALUES (:id, :userId, :accountId, :categoryId, :type, :amount, :currencyCode, :description,
                    :frequency, :interval, :dayOfWeek, :dayOfMonth, :startDate, :endDate,
                    :occurrenceLimit, :generatedCount, :priorCount, :status)
            """;

    /**
     * Activa es no cancelada y con ocurrencias por venir: toda sin fin, y una con fin mientras le quede
     * alguna posterior a now(). La proxima sale de las filas que existen, asi que una borrada a mano no
     * cuenta.
     */
    private static final String ACTIVAS = """
            SELECT %s, p.proxima
              FROM finance.recurrences r
              LEFT JOIN LATERAL (SELECT MIN(t.occurred_at) AS proxima
                                   FROM finance.transactions t
                                  WHERE t.recurrence_id = r.id
                                    AND t.occurred_at > now()) p ON TRUE
             WHERE r.user_id = :userId
               AND r.status = 'ACTIVE'
               AND (p.proxima IS NOT NULL OR (r.end_date IS NULL AND r.occurrence_limit IS NULL))
             ORDER BY p.proxima NULLS LAST, r.id
            """.formatted(COLUMNAS);

    private static final String POR_ID = """
            SELECT %s
              FROM finance.recurrences r
             WHERE r.id = :id
               AND r.user_id = :userId
               AND r.status = 'ACTIVE'
            """.formatted(COLUMNAS);

    private static final String SIN_FIN = """
            SELECT %s
              FROM finance.recurrences r
             WHERE r.user_id = :userId
               AND r.status = 'ACTIVE'
               AND r.end_date IS NULL
               AND r.occurrence_limit IS NULL
            """.formatted(COLUMNAS);

    private static final String PROXIMA = """
            SELECT MIN(occurred_at) AS proxima
              FROM finance.transactions
             WHERE recurrence_id = :id
               AND occurred_at > now()
            """;

    /** La condicion sobre el contador es la que impide que dos lecturas a la vez creen lo mismo. */
    private static final String AVANZAR = """
            UPDATE finance.recurrences
               SET generated_count = :generatedCount
             WHERE id = :id
               AND generated_count = :esperado
               AND status = 'ACTIVE'
            """;

    private static final String ACTUALIZAR = """
            UPDATE finance.recurrences
               SET account_id = :accountId,
                   category_id = :categoryId,
                   amount = :amount,
                   description = :description,
                   frequency = :frequency,
                   interval_count = :interval,
                   day_of_week = :dayOfWeek,
                   day_of_month = :dayOfMonth,
                   start_date = :startDate,
                   generated_count = :generatedCount,
                   prior_count = :priorCount
             WHERE id = :id
               AND user_id = :userId
               AND status = 'ACTIVE'
            """;

    /**
     * Solo lo que el parche trae: una ocurrencia editada a mano conserva lo demas. El amount_base que va aqui
     * lo reemplaza trg_transactions_usd_equivalent por el equivalente en USD (FA-122). El trigger de saldos
     * revierte la fila vieja y aplica la nueva en cada saldo.
     */
    private static final String APLICAR_CAMBIOS = """
            UPDATE finance.transactions
               SET account_id = COALESCE(CAST(:accountId AS uuid), account_id),
                   category_id = COALESCE(CAST(:categoryId AS uuid), category_id),
                   amount = COALESCE(CAST(:amount AS numeric), amount),
                   amount_base = COALESCE(CAST(:amount AS numeric), amount_base),
                   description = COALESCE(CAST(:description AS varchar), description)
             WHERE recurrence_id = :id
               AND (:todas OR occurred_at > :ahora)
            """;

    private static final String BORRAR_FUTURAS = """
            DELETE FROM finance.transactions
             WHERE recurrence_id = :id
               AND occurred_at > :ahora
            """;

    private static final String CANCELAR = """
            UPDATE finance.recurrences
               SET status = 'CANCELLED'
             WHERE id = :id
               AND user_id = :userId
               AND status = 'ACTIVE'
            """;

    private final DatabaseClient databaseClient;
    private final TransactionRepositoryPort movimientos;
    private final TransactionalOperator transaccion;

    public RecurrenceR2dbcAdapter(DatabaseClient databaseClient, TransactionRepositoryPort movimientos,
            ReactiveTransactionManager txManager) {
        this.databaseClient = databaseClient;
        this.movimientos = movimientos;
        this.transaccion = TransactionalOperator.create(txManager);
    }

    @Override
    public Mono<Void> save(Recurrence serie, List<Transaction> ocurrencias) {
        DatabaseClient.GenericExecuteSpec sentencia = conRegla(databaseClient.sql(INSERTAR), serie);
        sentencia = serie.endDate() == null
                ? sentencia.bindNull("endDate", LocalDate.class)
                : sentencia.bind("endDate", serie.endDate());
        sentencia = serie.occurrenceLimit() == null
                ? sentencia.bindNull("occurrenceLimit", Integer.class)
                : sentencia.bind("occurrenceLimit", serie.occurrenceLimit());
        return sentencia
                .bind("userId", serie.userId())
                .bind("type", serie.type().name())
                .bind("currencyCode", serie.currencyCode())
                .bind("status", serie.status().name())
                .fetch().rowsUpdated()
                .thenMany(Flux.defer(() -> movimientos.saveAll(ocurrencias)))
                .then()
                .as(transaccion::transactional);
    }

    @Override
    public Flux<RecurrenceView> findActiveByUser(UUID userId) {
        return databaseClient.sql(ACTIVAS)
                .bind("userId", userId)
                .map((row, metadata) -> new RecurrenceView(toDomain(row), instante(row, "proxima")))
                .all();
    }

    @Override
    public Mono<Recurrence> findActiveByIdAndUser(UUID id, UUID userId) {
        return databaseClient.sql(POR_ID)
                .bind("id", id)
                .bind("userId", userId)
                .map((row, metadata) -> toDomain(row))
                .one();
    }

    @Override
    public Flux<Recurrence> findOpenActiveByUser(UUID userId) {
        return databaseClient.sql(SIN_FIN)
                .bind("userId", userId)
                .map((row, metadata) -> toDomain(row))
                .all();
    }

    /** MIN sin filas da una fila con NULL: se vuelve vacio. */
    @Override
    public Mono<Instant> findNextOccurrence(UUID recurrenceId) {
        return databaseClient.sql(PROXIMA)
                .bind("id", recurrenceId)
                .map((row, metadata) -> Optional.ofNullable(instante(row, "proxima")))
                .one()
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<Boolean> append(Recurrence serie, int esperado, List<Transaction> ocurrencias) {
        return databaseClient.sql(AVANZAR)
                .bind("id", serie.id())
                .bind("generatedCount", serie.generatedCount())
                .bind("esperado", esperado)
                .fetch().rowsUpdated()
                .flatMap(filas -> filas == 0
                        ? Mono.just(false)
                        : movimientos.saveAll(ocurrencias).then(Mono.just(true)))
                .as(transaccion::transactional);
    }

    @Override
    public Mono<Boolean> update(Recurrence serie, GroupScope alcance, Instant ahora,
            RecurrenceTemplateChange cambios, List<Transaction> rehechas) {
        Mono<Void> futuras = rehechas == null
                ? Mono.empty()
                : borrarFuturas(serie.id(), ahora);
        Mono<Void> nuevas = rehechas == null
                ? Mono.empty()
                : Mono.defer(() -> movimientos.saveAll(rehechas).then());
        return conRegla(databaseClient.sql(ACTUALIZAR), serie)
                .bind("userId", serie.userId())
                .fetch().rowsUpdated()
                .flatMap(filas -> filas == 0
                        ? Mono.just(false)
                        : futuras.then(aplicar(serie.id(), alcance, ahora, cambios)).then(nuevas)
                                .then(Mono.just(true)))
                .as(transaccion::transactional)
                .onErrorMap(SinTasaDeCambio::traducir);
    }

    @Override
    public Mono<Boolean> cancel(UUID id, UUID userId, Instant ahora) {
        return databaseClient.sql(CANCELAR)
                .bind("id", id)
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .flatMap(filas -> filas == 0
                        ? Mono.just(false)
                        : borrarFuturas(id, ahora).then(Mono.just(true)))
                .as(transaccion::transactional);
    }

    private Mono<Void> borrarFuturas(UUID id, Instant ahora) {
        return databaseClient.sql(BORRAR_FUTURAS)
                .bind("id", id)
                .bind("ahora", OffsetDateTime.ofInstant(ahora, ZoneOffset.UTC))
                .fetch().rowsUpdated()
                .then();
    }

    /** Un parche sin cambios de plantilla, como uno que solo cambia la periodicidad, no toca las filas. */
    private Mono<Void> aplicar(UUID id, GroupScope alcance, Instant ahora, RecurrenceTemplateChange cambios) {
        if (cambios.accountId() == null && cambios.categoryId() == null && cambios.amount() == null
                && cambios.description() == null) {
            return Mono.empty();
        }
        DatabaseClient.GenericExecuteSpec sentencia = databaseClient.sql(APLICAR_CAMBIOS)
                .bind("id", id)
                .bind("todas", alcance == GroupScope.ALL)
                .bind("ahora", OffsetDateTime.ofInstant(ahora, ZoneOffset.UTC));
        sentencia = cambios.accountId() == null
                ? sentencia.bindNull("accountId", UUID.class)
                : sentencia.bind("accountId", cambios.accountId());
        sentencia = cambios.categoryId() == null
                ? sentencia.bindNull("categoryId", UUID.class)
                : sentencia.bind("categoryId", cambios.categoryId());
        sentencia = cambios.amount() == null
                ? sentencia.bindNull("amount", BigDecimal.class)
                : sentencia.bind("amount", cambios.amount());
        sentencia = cambios.description() == null
                ? sentencia.bindNull("description", String.class)
                : sentencia.bind("description", cambios.description());
        return sentencia.fetch().rowsUpdated().then();
    }

    /** Los parametros que el INSERT y el UPDATE comparten: plantilla, regla y contadores. El fin no cambia. */
    private static DatabaseClient.GenericExecuteSpec conRegla(DatabaseClient.GenericExecuteSpec sentencia,
            Recurrence serie) {
        RecurrenceRule regla = serie.rule();
        sentencia = sentencia
                .bind("id", serie.id())
                .bind("accountId", serie.accountId())
                .bind("categoryId", serie.categoryId())
                .bind("amount", serie.amount())
                .bind("description", serie.description())
                .bind("frequency", regla.frequency().name())
                .bind("interval", regla.interval())
                .bind("startDate", regla.startDate())
                .bind("generatedCount", serie.generatedCount())
                .bind("priorCount", serie.priorCount());
        sentencia = regla.dayOfWeek() == null
                ? sentencia.bindNull("dayOfWeek", Integer.class)
                : sentencia.bind("dayOfWeek", regla.dayOfWeek().getValue());
        sentencia = regla.dayOfMonth() == null
                ? sentencia.bindNull("dayOfMonth", Integer.class)
                : sentencia.bind("dayOfMonth", regla.dayOfMonth());
        return sentencia;
    }

    private static Instant instante(Row row, String columna) {
        OffsetDateTime valor = row.get(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    private static Recurrence toDomain(Row row) {
        Integer diaSemana = entero(row, "day_of_week");
        RecurrenceRule regla = new RecurrenceRule(
                Frequency.valueOf(row.get("frequency", String.class)),
                entero(row, "interval_count"),
                diaSemana == null ? null : DayOfWeek.of(diaSemana),
                entero(row, "day_of_month"),
                row.get("start_date", LocalDate.class));
        return new Recurrence(
                row.get("id", UUID.class),
                row.get("user_id", UUID.class),
                TransactionType.valueOf(row.get("type", String.class)),
                row.get("account_id", UUID.class),
                row.get("category_id", UUID.class),
                row.get("amount", BigDecimal.class),
                row.get("currency_code", String.class),
                row.get("description", String.class),
                regla,
                row.get("end_date", LocalDate.class),
                entero(row, "occurrence_limit"),
                entero(row, "generated_count"),
                entero(row, "prior_count"),
                RecurrenceStatus.valueOf(row.get("status", String.class)));
    }

    /** SMALLINT e INTEGER llegan como Short o Integer segun la columna. */
    private static Integer entero(Row row, String columna) {
        Number valor = row.get(columna, Number.class);
        return valor == null ? null : valor.intValue();
    }
}
