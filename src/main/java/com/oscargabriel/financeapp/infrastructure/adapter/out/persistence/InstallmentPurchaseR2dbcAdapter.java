package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.oscargabriel.financeapp.domain.model.GroupScope;
import com.oscargabriel.financeapp.domain.model.InstallmentPurchase;
import com.oscargabriel.financeapp.domain.model.InstallmentPurchaseView;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.port.out.InstallmentPurchaseRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Compras en cuotas de FA-108. Las cuotas se insertan con TransactionRepositoryPort.saveAll dentro de la
 * transaccion de la compra: su TransactionalOperator se une a la que ya esta en curso. El resumen sale de
 * las cuotas que existen con fecha posterior a now(): una borrada a mano cuenta como pagada.
 */
@Component
public class InstallmentPurchaseR2dbcAdapter implements InstallmentPurchaseRepositoryPort {

    private static final String INSERTAR = """
            INSERT INTO finance.installment_purchases
                   (id, user_id, account_id, category_id, amount, currency_code, description,
                    purchase_date, installment_count, monthly_interest_rate)
            VALUES (:id, :userId, :accountId, :categoryId, :amount, :currencyCode, :description,
                    :purchaseDate, :installmentCount, :monthlyInterestRate)
            """;

    private static final String RESUMEN = """
            SELECT p.id, p.user_id, p.account_id, p.category_id, p.amount, p.currency_code, p.description,
                   p.purchase_date, p.installment_count, p.monthly_interest_rate,
                   f.pendientes, f.capital, f.monto,
                   n.id AS proxima_id, n.installment_number AS proxima_numero,
                   n.occurred_at AS proxima_fecha, n.amount AS proxima_monto
              FROM finance.installment_purchases p
             CROSS JOIN LATERAL (SELECT count(*) AS pendientes,
                                        COALESCE(SUM(t.installment_principal), 0) AS capital,
                                        COALESCE(SUM(t.amount), 0) AS monto
                                   FROM finance.transactions t
                                  WHERE t.installment_purchase_id = p.id
                                    AND t.occurred_at > now()) f
              LEFT JOIN LATERAL (SELECT t.id, t.installment_number, t.occurred_at, t.amount
                                   FROM finance.transactions t
                                  WHERE t.installment_purchase_id = p.id
                                    AND t.occurred_at > now()
                                  ORDER BY t.occurred_at, t.installment_number
                                  LIMIT 1) n ON TRUE
             WHERE p.user_id = :userId
               AND p.status = 'ACTIVE'
            """;

    private static final String ACTIVAS = RESUMEN + """
               AND f.pendientes > 0
             ORDER BY n.occurred_at, p.id
            """;

    private static final String POR_ID = RESUMEN + """
               AND p.id = :id
            """;

    private static final String ACTUALIZAR = """
            UPDATE finance.installment_purchases
               SET description = COALESCE(CAST(:description AS varchar), description),
                   category_id = COALESCE(CAST(:categoryId AS uuid), category_id)
             WHERE id = :id
               AND user_id = :userId
               AND status = 'ACTIVE'
            """;

    /** Solo lo que el parche trae. Ni el monto ni la cuenta cambian: ningun saldo se mueve. */
    private static final String APLICAR_CAMBIOS = """
            UPDATE finance.transactions
               SET description = COALESCE(CAST(:description AS varchar), description),
                   category_id = COALESCE(CAST(:categoryId AS uuid), category_id)
             WHERE installment_purchase_id = :id
               AND (:todas OR occurred_at > :ahora)
            """;

    private static final String CANCELAR = """
            UPDATE finance.installment_purchases
               SET status = 'CANCELLED'
             WHERE id = :id
               AND user_id = :userId
               AND status = 'ACTIVE'
            """;

    /** El trigger revierte su efecto en el saldo, y su capital deja de estar comprometido. */
    private static final String BORRAR_FUTURAS = """
            DELETE FROM finance.transactions
             WHERE installment_purchase_id = :id
               AND occurred_at > :ahora
            """;

    private final DatabaseClient databaseClient;
    private final TransactionRepositoryPort movimientos;
    private final TransactionalOperator transaccion;

    public InstallmentPurchaseR2dbcAdapter(DatabaseClient databaseClient, TransactionRepositoryPort movimientos,
            ReactiveTransactionManager txManager) {
        this.databaseClient = databaseClient;
        this.movimientos = movimientos;
        this.transaccion = TransactionalOperator.create(txManager);
    }

    @Override
    public Mono<Void> save(InstallmentPurchase compra, List<Transaction> cuotas) {
        return databaseClient.sql(INSERTAR)
                .bind("id", compra.id())
                .bind("userId", compra.userId())
                .bind("accountId", compra.accountId())
                .bind("categoryId", compra.categoryId())
                .bind("amount", compra.amount())
                .bind("currencyCode", compra.currencyCode())
                .bind("description", compra.description())
                .bind("purchaseDate", compra.purchaseDate())
                .bind("installmentCount", (short) compra.installmentCount())
                .bind("monthlyInterestRate", compra.monthlyInterestRate())
                .fetch().rowsUpdated()
                .thenMany(Flux.defer(() -> movimientos.saveAll(cuotas)))
                .then()
                .as(transaccion::transactional);
    }

    @Override
    public Flux<InstallmentPurchaseView> findActiveByUser(UUID userId) {
        return databaseClient.sql(ACTIVAS)
                .bind("userId", userId)
                .map((row, metadata) -> toView(row))
                .all();
    }

    @Override
    public Mono<InstallmentPurchaseView> findByIdAndUser(UUID id, UUID userId) {
        return databaseClient.sql(POR_ID)
                .bind("id", id)
                .bind("userId", userId)
                .map((row, metadata) -> toView(row))
                .one();
    }

    @Override
    public Mono<Boolean> update(UUID id, UUID userId, GroupScope alcance, Instant ahora, String description,
            UUID categoryId) {
        return conCambios(databaseClient.sql(ACTUALIZAR), description, categoryId)
                .bind("id", id)
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .flatMap(filas -> filas == 0
                        ? Mono.just(false)
                        : conCambios(databaseClient.sql(APLICAR_CAMBIOS), description, categoryId)
                                .bind("id", id)
                                .bind("todas", alcance == GroupScope.ALL)
                                .bind("ahora", OffsetDateTime.ofInstant(ahora, ZoneOffset.UTC))
                                .fetch().rowsUpdated()
                                .thenReturn(true))
                .as(transaccion::transactional);
    }

    @Override
    public Mono<Boolean> cancel(UUID id, UUID userId, Instant ahora) {
        return databaseClient.sql(CANCELAR)
                .bind("id", id)
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .flatMap(filas -> filas == 0
                        ? Mono.just(false)
                        : databaseClient.sql(BORRAR_FUTURAS)
                                .bind("id", id)
                                .bind("ahora", OffsetDateTime.ofInstant(ahora, ZoneOffset.UTC))
                                .fetch().rowsUpdated()
                                .thenReturn(true))
                .as(transaccion::transactional);
    }

    /** null es "no cambia", y R2DBC exige el tipo para enlazar un null. */
    private static DatabaseClient.GenericExecuteSpec conCambios(DatabaseClient.GenericExecuteSpec sentencia,
            String description, UUID categoryId) {
        sentencia = description == null
                ? sentencia.bindNull("description", String.class)
                : sentencia.bind("description", description);
        return categoryId == null
                ? sentencia.bindNull("categoryId", UUID.class)
                : sentencia.bind("categoryId", categoryId);
    }

    private static InstallmentPurchaseView toView(Row row) {
        InstallmentPurchase compra = new InstallmentPurchase(
                row.get("id", UUID.class),
                row.get("user_id", UUID.class),
                row.get("account_id", UUID.class),
                row.get("category_id", UUID.class),
                row.get("amount", BigDecimal.class),
                row.get("currency_code", String.class),
                row.get("description", String.class),
                row.get("purchase_date", LocalDate.class),
                row.get("installment_count", Number.class).intValue(),
                row.get("monthly_interest_rate", BigDecimal.class));
        UUID proxima = row.get("proxima_id", UUID.class);
        return new InstallmentPurchaseView(
                compra,
                compra.installmentCount() - row.get("pendientes", Number.class).intValue(),
                row.get("capital", BigDecimal.class),
                row.get("monto", BigDecimal.class),
                proxima == null ? null : new InstallmentPurchaseView.NextInstallment(
                        row.get("proxima_numero", Number.class).intValue(),
                        proxima,
                        row.get("proxima_fecha", OffsetDateTime.class).toInstant(),
                        row.get("proxima_monto", BigDecimal.class)));
    }
}
