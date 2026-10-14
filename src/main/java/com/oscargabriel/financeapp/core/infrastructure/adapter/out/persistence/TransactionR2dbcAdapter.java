package com.oscargabriel.financeapp.core.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.oscargabriel.financeapp.core.domain.model.ExpenseShare;
import com.oscargabriel.financeapp.core.domain.model.InstallmentRef;
import com.oscargabriel.financeapp.core.domain.model.OriginalAmount;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TransactionR2dbcAdapter implements TransactionRepositoryPort {

    /**
     * exchange_rate y amount_base los fija trg_transactions_usd_equivalent con el equivalente en USD (FA-122);
     * amount_base va con amount solo porque la columna es NOT NULL y la app nueva puede llegar antes que el
     * update que crea el trigger. current_balance lo mueve trg_transactions_sync_balance fila a fila, dentro de
     * la misma transaccion, y solo si el movimiento entra CONFIRMED.
     */
    private static final String INSERTAR = """
            INSERT INTO finance.transactions
                   (id, user_id, account_id, destination_account_id, category_id, type,
                    amount, currency_code, amount_base, description, notes, occurred_at, status, origin, recurrence_id,
                    installment_purchase_id, installment_number, installment_principal,
                    destination_amount, original_amount, original_currency_code, group_id, shared_by_user_id, share_id)
            VALUES (:id, :userId, :accountId, :destinationAccountId, :categoryId, :type,
                    :amount, :currencyCode, :amount, :description, :notes, :occurredAt, :status, :origin, :recurrenceId,
                    :installmentPurchaseId, :installmentNumber, :installmentPrincipal,
                    :destinationAmount, :originalAmount, :originalCurrencyCode, :groupId, :sharedByUserId, :shareId)
            """;

    private static final String INSERTAR_PARTE = """
            INSERT INTO finance.expense_shares
                   (id, source_transaction_id, payer_id, debtor_id, amount, currency_code, description,
                    occurred_at, status)
            VALUES (:id, :sourceTransactionId, :payerId, :debtorId, :amount, :currencyCode, :description,
                    :occurredAt, :status)
            """;

    /** La compra de una cuota da su total de cuotas (FA-108). */
    private static final String COLUMNAS = """
            t.id, t.user_id, t.type, t.account_id, t.destination_account_id, t.category_id,
                   t.amount, t.currency_code, t.description, t.notes, t.occurred_at, t.status, t.origin,
                   t.recurrence_id, t.destination_amount, t.original_amount, t.original_currency_code, t.group_id,
                   t.shared_by_user_id, t.share_id, %s"""
            .formatted(CuotaDeLaFila.COLUMNAS);

    private static final String BUSCAR = """
            SELECT %s
              FROM finance.transactions t
              %s
             WHERE t.id = :id
               AND t.user_id = :userId
            """.formatted(COLUMNAS, CuotaDeLaFila.JOIN);

    private static final String PENDIENTES = """
            SELECT %s
              FROM finance.transactions t
              %s
             WHERE t.user_id = :userId
               AND t.status = 'PENDING'
             ORDER BY t.occurred_at DESC, t.id DESC
            """.formatted(COLUMNAS, CuotaDeLaFila.JOIN);

    /**
     * notes, currency_code y el monto original no se tocan: no son modificables. destination_amount si: el PATCH
     * lo ajusta, o lo limpia al dejar de ser una transferencia entre monedas (FA-51). Tampoco la serie ni la cuota: un movimiento
     * editado sigue en su grupo. El trigger revierte la fila vieja y aplica la nueva, asi que un cambio de
     * cuenta, tipo o monto deja los saldos coherentes.
     */
    private static final String ACTUALIZAR = """
            UPDATE finance.transactions
               SET type = :type,
                   account_id = :accountId,
                   destination_account_id = :destinationAccountId,
                   category_id = :categoryId,
                   amount = :amount,
                   amount_base = :amount,
                   destination_amount = :destinationAmount,
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

    /**
     * Un pendiente que llego sin cuenta toma la elegida al aprobarse (FIN-66), con el monto ya en su moneda. El
     * trigger de la tasa recalcula amount_base porque cambian el monto y la moneda; el de saldos aplica la fila.
     */
    private static final String CONFIRMAR_CON = """
            UPDATE finance.transactions
               SET status = 'CONFIRMED',
                   account_id = :accountId,
                   category_id = :categoryId,
                   amount = :amount,
                   amount_base = :amount,
                   currency_code = :currencyCode,
                   original_amount = :originalAmount,
                   original_currency_code = :originalCurrencyCode
             WHERE id = :id
               AND user_id = :userId
               AND status = 'PENDING'
            """;

    private static final String PAGAR_PARTE = """
            UPDATE finance.expense_shares
               SET status = 'PAID',
                   resolved_at = now()
             WHERE id = :shareId
               AND debtor_id = :userId
               AND status = 'PENDING'
            """;

    /**
     * Antes de borrar pendientes de quien debe: su parte queda rechazada, y deja de ser deuda (FIN-66). Solo los que
     * siguen pendientes, como el DELETE que sigue.
     */
    private static final String RECHAZAR_PARTES = """
            UPDATE finance.expense_shares s
               SET status = 'REJECTED',
                   resolved_at = now()
              FROM finance.transactions t
             WHERE t.id = ANY(:ids)
               AND t.user_id = :userId
               AND t.status = 'PENDING'
               AND t.type = 'EXPENSE'
               AND s.id = t.share_id
               AND s.debtor_id = :userId
               AND s.status = 'PENDING'
            """;

    /**
     * Antes de borrar el gasto de quien pago: sus partes pendientes quedan anuladas y los pendientes de esas partes
     * se borran (FIN-66). Va antes del DELETE del gasto, que pone source_transaction_id en NULL. Las pagadas y las
     * rechazadas no cambian.
     */
    private static final String ANULAR_PARTES = """
            WITH anuladas AS (
                UPDATE finance.expense_shares
                   SET status = 'VOIDED',
                       resolved_at = now()
                 WHERE source_transaction_id = ANY(:ids)
                   AND payer_id = :userId
                   AND status = 'PENDING'
             RETURNING id
            )
            DELETE FROM finance.transactions
             WHERE share_id IN (SELECT id FROM anuladas)
               AND status = 'PENDING'
            """;

    private static final String CON_PARTES_PENDIENTES = """
            SELECT EXISTS (
                SELECT 1
                  FROM finance.expense_shares
                 WHERE source_transaction_id = :id
                   AND status = 'PENDING'
            )
            """;

    private static final String BORRAR_PENDIENTE = """
            DELETE FROM finance.transactions
             WHERE id = :id
               AND user_id = :userId
               AND status = 'PENDING'
            """;

    /** El orden por id es el del lote que lo creo: el alta reparte ids crecientes (FIN-58). */
    private static final String DEL_GRUPO = """
            SELECT %s
              FROM finance.transactions t
              %s
             WHERE t.group_id = :groupId
               AND t.user_id = :userId
             ORDER BY t.id
            """.formatted(COLUMNAS, CuotaDeLaFila.JOIN);

    /** Como CONFIRMAR, para varios: el trigger aplica cada fila dentro de la misma transaccion. */
    private static final String CONFIRMAR_VARIOS = """
            UPDATE finance.transactions
               SET status = 'CONFIRMED'
             WHERE id = ANY(:ids)
               AND user_id = :userId
               AND status = 'PENDING'
            """;

    private static final String BORRAR_PENDIENTES = """
            DELETE FROM finance.transactions
             WHERE id = ANY(:ids)
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
                .onErrorMap(SinTasaDeCambio::traducir)
                .flatMapIterable(guardadas -> guardadas);
    }

    /**
     * En el orden en que se referencian: el movimiento, su parte y el pendiente de la parte (FIN-66). Lo de otras
     * personas no se devuelve: es de ellas.
     */
    @Override
    public Flux<Transaction> saveAll(List<Transaction> transacciones, List<ExpenseShare> partes,
            List<Transaction> deOtros) {
        return Flux.fromIterable(transacciones)
                .concatMap(t -> insertar(t).thenReturn(t))
                .collectList()
                .flatMap(guardadas -> Flux.fromIterable(partes).concatMap(this::insertarParte)
                        .thenMany(Flux.fromIterable(deOtros).concatMap(this::insertar))
                        .then(Mono.just(guardadas)))
                .as(transaccion::transactional)
                .onErrorMap(SinTasaDeCambio::traducir)
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
                .onErrorMap(SinTasaDeCambio::traducir)
                .map(filas -> filas > 0);
    }

    @Override
    public Mono<Boolean> deleteByIdAndUser(UUID id, UUID userId) {
        return borrarUnoConPartes(BORRAR, id, userId);
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
    public Mono<Boolean> confirmWith(Transaction confirmado) {
        return confirmarCon(confirmado)
                .onErrorMap(SinTasaDeCambio::traducir)
                .map(filas -> filas > 0);
    }

    /**
     * Si el pendiente o la parte ya no estaban pendientes (otro request los aprobo, los rechazo o borro el gasto en
     * medio), el error deshace la transaccion: la parte nunca queda pagada sin el gasto confirmado ni al reves.
     */
    @Override
    public Mono<Boolean> payShare(Transaction confirmado, Transaction ingresoDeQuienPago) {
        return confirmarCon(confirmado)
                .flatMap(this::exigirUna)
                .then(databaseClient.sql(PAGAR_PARTE)
                        .bind("shareId", confirmado.shareId())
                        .bind("userId", confirmado.userId())
                        .fetch().rowsUpdated())
                .flatMap(this::exigirUna)
                .then(insertar(ingresoDeQuienPago))
                .thenReturn(true)
                .as(transaccion::transactional)
                .onErrorMap(SinTasaDeCambio::traducir)
                .onErrorResume(GrupoCambiado.class, e -> Mono.just(false));
    }

    private Mono<Long> exigirUna(Long filas) {
        return filas == 1 ? Mono.just(filas) : Mono.error(new GrupoCambiado());
    }

    private Mono<Long> confirmarCon(Transaction t) {
        DatabaseClient.GenericExecuteSpec sentencia = databaseClient.sql(CONFIRMAR_CON)
                .bind("id", t.id())
                .bind("userId", t.userId())
                .bind("accountId", t.accountId())
                .bind("categoryId", t.categoryId())
                .bind("amount", t.amount())
                .bind("currencyCode", t.currencyCode());
        OriginalAmount original = t.original();
        sentencia = original == null
                ? sentencia.bindNull("originalAmount", BigDecimal.class).bindNull("originalCurrencyCode", String.class)
                : sentencia.bind("originalAmount", original.amount())
                        .bind("originalCurrencyCode", original.currencyCode());
        return sentencia.fetch().rowsUpdated();
    }

    @Override
    public Mono<Boolean> hasPendingShares(UUID sourceTransactionId) {
        return databaseClient.sql(CON_PARTES_PENDIENTES)
                .bind("id", sourceTransactionId)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one();
    }

    @Override
    public Mono<Boolean> deletePending(UUID id, UUID userId) {
        return borrarUnoConPartes(BORRAR_PENDIENTE, id, userId);
    }

    @Override
    public Flux<Transaction> findByGroup(UUID groupId, UUID userId) {
        return databaseClient.sql(DEL_GRUPO)
                .bind("groupId", groupId)
                .bind("userId", userId)
                .map((row, metadata) -> toDomain(row))
                .all();
    }

    /**
     * Si otro request aprobo o borro alguno en medio, actualiza menos filas: el error deshace la transaccion, para
     * que el grupo no quede confirmado a medias.
     */
    @Override
    public Mono<Boolean> confirmAll(List<UUID> ids, UUID userId) {
        return databaseClient.sql(CONFIRMAR_VARIOS)
                .bind("ids", ids.toArray(UUID[]::new))
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .flatMap(filas -> filas == ids.size() ? Mono.just(true) : Mono.<Boolean>error(new GrupoCambiado()))
                .as(transaccion::transactional)
                .onErrorResume(GrupoCambiado.class, e -> Mono.just(false));
    }

    @Override
    public Mono<Long> deletePendingIn(List<UUID> ids, UUID userId) {
        UUID[] arreglo = ids.toArray(UUID[]::new);
        return conPartes(arreglo, userId, databaseClient.sql(BORRAR_PENDIENTES)
                        .bind("ids", arreglo)
                        .bind("userId", userId)
                        .fetch().rowsUpdated())
                .as(transaccion::transactional);
    }

    /**
     * Borrar arrastra las partes (FIN-66): si el borrado no afecta la fila, el error deshace tambien lo que se hizo
     * con ellas.
     */
    private Mono<Boolean> borrarUnoConPartes(String sql, UUID id, UUID userId) {
        return conPartes(new UUID[] {id}, userId, databaseClient.sql(sql)
                        .bind("id", id)
                        .bind("userId", userId)
                        .fetch().rowsUpdated())
                .flatMap(filas -> filas > 0 ? Mono.just(true) : Mono.<Boolean>error(new GrupoCambiado()))
                .as(transaccion::transactional)
                .onErrorResume(GrupoCambiado.class, e -> Mono.just(false));
    }

    /** Rechaza la parte de los pendientes de quien debe y anula las de los gastos de quien pago, y luego borra. */
    private Mono<Long> conPartes(UUID[] ids, UUID userId, Mono<Long> borrado) {
        return databaseClient.sql(RECHAZAR_PARTES)
                .bind("ids", ids)
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .then(databaseClient.sql(ANULAR_PARTES)
                        .bind("ids", ids)
                        .bind("userId", userId)
                        .fetch().rowsUpdated())
                .then(borrado);
    }

    /** Solo existe para forzar el rollback de confirmAll, de payShare y de un borrado que no afecto la fila. */
    private static final class GrupoCambiado extends RuntimeException {

        GrupoCambiado() {
            super(null, null, false, false);
        }
    }

    private Mono<Boolean> porIdYUsuario(String sql, UUID id, UUID userId) {
        return databaseClient.sql(sql)
                .bind("id", id)
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .map(filas -> filas > 0);
    }

    private Mono<Long> insertarParte(ExpenseShare parte) {
        return databaseClient.sql(INSERTAR_PARTE)
                .bind("id", parte.id())
                .bind("sourceTransactionId", parte.sourceTransactionId())
                .bind("payerId", parte.payerId())
                .bind("debtorId", parte.debtorId())
                .bind("amount", parte.amount())
                .bind("currencyCode", parte.currencyCode())
                .bind("description", parte.description())
                .bind("occurredAt", OffsetDateTime.ofInstant(parte.occurredAt(), ZoneOffset.UTC))
                .bind("status", parte.status().name())
                .fetch().rowsUpdated();
    }

    private Mono<Long> insertar(Transaction t) {
        DatabaseClient.GenericExecuteSpec sentencia = conCamposComunes(databaseClient.sql(INSERTAR), t)
                .bind("currencyCode", t.currencyCode())
                .bind("status", t.status().name())
                .bind("origin", t.origin().name());
        OriginalAmount original = t.original();
        sentencia = original == null
                ? sentencia.bindNull("originalAmount", BigDecimal.class)
                        .bindNull("originalCurrencyCode", String.class)
                : sentencia.bind("originalAmount", original.amount())
                        .bind("originalCurrencyCode", original.currencyCode());
        sentencia = t.notes() == null
                ? sentencia.bindNull("notes", String.class)
                : sentencia.bind("notes", t.notes());
        sentencia = t.recurrenceId() == null
                ? sentencia.bindNull("recurrenceId", UUID.class)
                : sentencia.bind("recurrenceId", t.recurrenceId());
        InstallmentRef cuota = t.installment();
        sentencia = cuota == null
                ? sentencia.bindNull("installmentPurchaseId", UUID.class)
                        .bindNull("installmentNumber", Short.class)
                        .bindNull("installmentPrincipal", BigDecimal.class)
                : sentencia.bind("installmentPurchaseId", cuota.purchaseId())
                        .bind("installmentNumber", (short) cuota.number())
                        .bind("installmentPrincipal", cuota.principal());
        sentencia = opcional(sentencia, "groupId", t.groupId());
        sentencia = opcional(sentencia, "sharedByUserId", t.sharedByUserId());
        sentencia = opcional(sentencia, "shareId", t.shareId());
        return sentencia.fetch().rowsUpdated();
    }

    private static DatabaseClient.GenericExecuteSpec opcional(DatabaseClient.GenericExecuteSpec sentencia,
            String nombre, UUID valor) {
        return valor == null ? sentencia.bindNull(nombre, UUID.class) : sentencia.bind(nombre, valor);
    }

    /** Los parametros que el INSERT y el UPDATE comparten, con sus nulos tipados. */
    private static DatabaseClient.GenericExecuteSpec conCamposComunes(DatabaseClient.GenericExecuteSpec sentencia,
            Transaction t) {
        sentencia = sentencia
                .bind("id", t.id())
                .bind("userId", t.userId())
                .bind("type", t.type().name())
                .bind("amount", t.amount())
                .bind("description", t.description())
                .bind("occurredAt", OffsetDateTime.ofInstant(t.occurredAt(), ZoneOffset.UTC));
        // Un pendiente compartido llega sin cuenta hasta que lo aprueban (FIN-66).
        sentencia = opcional(sentencia, "accountId", t.accountId());
        sentencia = t.destinationAmount() == null
                ? sentencia.bindNull("destinationAmount", BigDecimal.class)
                : sentencia.bind("destinationAmount", t.destinationAmount());
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
                TransactionStatus.valueOf(row.get("status", String.class)),
                TransactionOrigin.valueOf(row.get("origin", String.class)),
                row.get("recurrence_id", UUID.class),
                CuotaDeLaFila.de(row),
                row.get("destination_amount", BigDecimal.class),
                original(row),
                row.get("group_id", UUID.class),
                row.get("shared_by_user_id", UUID.class),
                row.get("share_id", UUID.class));
    }

    private static OriginalAmount original(Row row) {
        String moneda = row.get("original_currency_code", String.class);
        return moneda == null ? null : new OriginalAmount(row.get("original_amount", BigDecimal.class), moneda);
    }
}
