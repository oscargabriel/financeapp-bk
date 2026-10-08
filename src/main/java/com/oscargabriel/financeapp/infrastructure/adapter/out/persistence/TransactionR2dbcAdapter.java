package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TransactionR2dbcAdapter implements TransactionRepositoryPort {

    /**
     * Solo COP: exchange_rate queda en su DEFAULT 1 y amount_base es el mismo amount. current_balance
     * lo mueve trg_transactions_sync_balance fila a fila, dentro de la misma transaccion, y solo si el
     * movimiento entra CONFIRMED.
     */
    private static final String INSERTAR = """
            INSERT INTO finance.transactions
                   (id, user_id, account_id, destination_account_id, category_id, type,
                    amount, currency_code, amount_base, description, notes, occurred_at, status)
            VALUES (:id, :userId, :accountId, :destinationAccountId, :categoryId, :type,
                    :amount, :currencyCode, :amount, :description, :notes, :occurredAt, :status)
            """;

    private static final String COLUMNAS = """
            id, user_id, type, account_id, destination_account_id, category_id,
                   amount, currency_code, description, notes, occurred_at, status""";

    private static final String BUSCAR = """
            SELECT %s
              FROM finance.transactions
             WHERE id = :id
               AND user_id = :userId
            """.formatted(COLUMNAS);

    private static final String PENDIENTES = """
            SELECT %s
              FROM finance.transactions
             WHERE user_id = :userId
               AND status = 'PENDING'
             ORDER BY occurred_at DESC, id DESC
            """.formatted(COLUMNAS);

    /**
     * notes y currency_code no se tocan: no son modificables. El trigger revierte la fila vieja y
     * aplica la nueva, asi que un cambio de cuenta, tipo o monto deja los saldos coherentes.
     */
    private static final String ACTUALIZAR = """
            UPDATE finance.transactions
               SET type = :type,
                   account_id = :accountId,
                   destination_account_id = :destinationAccountId,
                   category_id = :categoryId,
                   amount = :amount,
                   amount_base = :amount,
                   description = :description,
                   occurred_at = :occurredAt
             WHERE id = :id
               AND user_id = :userId
            """;

    private static final String BORRAR = """
            DELETE FROM finance.transactions
             WHERE id = :id
               AND user_id = :userId
            """;

    /**
     * La condicion de estado se repite aunque el caso de uso ya la leyo: si otro request lo aprobo o lo
     * borro en medio, son cero filas, y el saldo nunca se aplica dos veces. Los saldos los mueve el
     * trigger al ver el paso de PENDING a CONFIRMED.
     */
    private static final String CONFIRMAR = """
            UPDATE finance.transactions
               SET status = 'CONFIRMED'
             WHERE id = :id
               AND user_id = :userId
               AND status = 'PENDING'
            """;

    private static final String BORRAR_PENDIENTE = """
            DELETE FROM finance.transactions
             WHERE id = :id
               AND user_id = :userId
               AND status = 'PENDING'
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transaccion;

    public TransactionR2dbcAdapter(DatabaseClient databaseClient, ReactiveTransactionManager txManager) {
        this.databaseClient = databaseClient;
        this.transaccion = TransactionalOperator.create(txManager);
    }

    /**
     * concatMap y no flatMap: los INSERT salen en el orden del lote, y el lote vuelve en ese orden.
     * La lista se junta dentro de la transaccion y se emite despues del commit: aguas abajo nadie
     * ve un movimiento que un INSERT posterior pueda deshacer.
     */
    @Override
    public Flux<Transaction> saveAll(List<Transaction> transacciones) {
        return Flux.fromIterable(transacciones)
                .concatMap(t -> insertar(t).thenReturn(t))
                .collectList()
                .as(transaccion::transactional)
                .flatMapIterable(guardadas -> guardadas);
    }

    @Override
    public Mono<Transaction> findByIdAndUser(UUID id, UUID userId) {
        return databaseClient.sql(BUSCAR)
                .bind("id", id)
                .bind("userId", userId)
                .map((row, metadata) -> toDomain(row))
                .one();
    }

    @Override
    public Mono<Boolean> update(Transaction t) {
        return conCamposComunes(databaseClient.sql(ACTUALIZAR), t)
                .fetch().rowsUpdated()
                .map(filas -> filas > 0);
    }

    @Override
    public Mono<Boolean> deleteByIdAndUser(UUID id, UUID userId) {
        return porIdYUsuario(BORRAR, id, userId);
    }

    @Override
    public Flux<Transaction> findPendingByUser(UUID userId) {
        return databaseClient.sql(PENDIENTES)
                .bind("userId", userId)
                .map((row, metadata) -> toDomain(row))
                .all();
    }

    @Override
    public Mono<Boolean> confirm(UUID id, UUID userId) {
        return porIdYUsuario(CONFIRMAR, id, userId);
    }

    @Override
    public Mono<Boolean> deletePending(UUID id, UUID userId) {
        return porIdYUsuario(BORRAR_PENDIENTE, id, userId);
    }

    private Mono<Boolean> porIdYUsuario(String sql, UUID id, UUID userId) {
        return databaseClient.sql(sql)
                .bind("id", id)
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .map(filas -> filas > 0);
    }

    private Mono<Long> insertar(Transaction t) {
        DatabaseClient.GenericExecuteSpec sentencia = conCamposComunes(databaseClient.sql(INSERTAR), t)
                .bind("currencyCode", t.currencyCode())
                .bind("status", t.status().name());
        sentencia = t.notes() == null
                ? sentencia.bindNull("notes", String.class)
                : sentencia.bind("notes", t.notes());
        return sentencia.fetch().rowsUpdated();
    }

    /** Los parametros que el INSERT y el UPDATE comparten, con sus nulos tipados. */
    private static DatabaseClient.GenericExecuteSpec conCamposComunes(DatabaseClient.GenericExecuteSpec sentencia,
            Transaction t) {
        sentencia = sentencia
                .bind("id", t.id())
                .bind("userId", t.userId())
                .bind("accountId", t.accountId())
                .bind("type", t.type().name())
                .bind("amount", t.amount())
                .bind("description", t.description())
                .bind("occurredAt", OffsetDateTime.ofInstant(t.occurredAt(), ZoneOffset.UTC));
        sentencia = t.destinationAccountId() == null
                ? sentencia.bindNull("destinationAccountId", UUID.class)
                : sentencia.bind("destinationAccountId", t.destinationAccountId());
        return t.categoryId() == null
                ? sentencia.bindNull("categoryId", UUID.class)
                : sentencia.bind("categoryId", t.categoryId());
    }

    private static Transaction toDomain(Row row) {
        return new Transaction(
                row.get("id", UUID.class),
                row.get("user_id", UUID.class),
                TransactionType.valueOf(row.get("type", String.class)),
                row.get("account_id", UUID.class),
                row.get("destination_account_id", UUID.class),
                row.get("category_id", UUID.class),
                row.get("amount", BigDecimal.class),
                row.get("currency_code", String.class),
                row.get("description", String.class),
                row.get("notes", String.class),
                row.get("occurred_at", OffsetDateTime.class).toInstant(),
                TransactionStatus.valueOf(row.get("status", String.class)));
    }
}
