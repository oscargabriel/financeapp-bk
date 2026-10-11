package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.model.ReportedTransaction;
import com.oscargabriel.financeapp.domain.model.TransactionReportFilter;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.out.TransactionReportQueryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class TransactionReportR2dbcAdapter implements TransactionReportQueryPort {

    private static final String SQL_MONEDA = """
            SELECT base_currency_code FROM finance.users WHERE id = :userId
            """;

    /**
     * Los limites del rango se calculan sobre el dia en la zona del usuario, no aplicando la zona a la
     * columna: asi la condicion usa ix_transactions_user_date. Es el mismo corte que v_monthly_spending.
     * Los pendientes quedan fuera hasta aprobarse (FA-76). La compra da el total de cuotas (FA-108).
     * amount_base sale en la moneda de la persona, no la columna, que esta en USD (FA-122).
     */
    private static final String SQL_MOVIMIENTOS = """
            SELECT t.id, t.type, t.account_id, t.destination_account_id, t.category_id,
                   c.name AS category_name, t.amount, t.currency_code, %s AS amount_base,
                   t.description, t.notes, t.occurred_at, t.recurrence_id, %s
              FROM finance.transactions t
              JOIN finance.users u ON u.id = t.user_id
              LEFT JOIN finance.categories c ON c.id = t.category_id
              %s
             WHERE t.user_id = :userId
               AND t.status = 'CONFIRMED'
               AND t.occurred_at >=(CAST(:from AS date)::timestamp AT TIME ZONE u.timezone)
               AND t.occurred_at < ((CAST(:to AS date) + 1)::timestamp AT TIME ZONE u.timezone)
            """.formatted(MontoDeLaPersona.DE_T, CuotaDeLaFila.COLUMNAS, CuotaDeLaFila.JOIN);

    private static final String FILTRO_CATEGORIAS = "   AND t.category_id = ANY(:categoryIds)\n";
    /** Origen o destino en el mismo WHERE: una transferencia entre dos cuentas filtradas sale una vez. */
    private static final String FILTRO_CUENTAS =
            "   AND (t.account_id = ANY(:accountIds) OR t.destination_account_id = ANY(:accountIds))\n";
    private static final String FILTRO_TIPOS ="   AND t.type = ANY(:types)\n";
    private static final String ORDEN = " ORDER BY t.occurred_at DESC, t.id DESC";

    private final DatabaseClient databaseClient;

    @Override
    public Mono<String> findBaseCurrency(UUID userId) {
        return databaseClient.sql(SQL_MONEDA)
                .bind("userId", userId)
                .map((row, metadata) -> row.get("base_currency_code", String.class))
                .one();
    }

    /** Los filtros entran como fragmentos fijos solo cuando vienen; los valores siempre por bind. */
    @Override
    public Flux<ReportedTransaction> findByUser(UUID userId, TransactionReportFilter filter) {
        StringBuilder sql = new StringBuilder(SQL_MOVIMIENTOS);
        if (!filter.categoryIds().isEmpty()) {
            sql.append(FILTRO_CATEGORIAS);
        }
        if (!filter.accountIds().isEmpty()) {
            sql.append(FILTRO_CUENTAS);
        }
        if (!filter.types().isEmpty()) {
            sql.append(FILTRO_TIPOS);
        }
        sql.append(ORDEN);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
                .bind("userId", userId)
                .bind("from", filter.from())
                .bind("to", filter.to());
        if (!filter.categoryIds().isEmpty()) {
            spec = spec.bind("categoryIds", filter.categoryIds().toArray(UUID[]::new));
        }
        if (!filter.accountIds().isEmpty()) {
            spec = spec.bind("accountIds", filter.accountIds().toArray(UUID[]::new));
        }
        if (!filter.types().isEmpty()) {
            spec = spec.bind("types", filter.types().stream().map(Enum::name).toArray(String[]::new));
        }
        return spec.map((row, metadata) -> toDomain(row)).all();
    }

    private static ReportedTransaction toDomain(Row row) {
        return new ReportedTransaction(
                row.get("id", UUID.class),
                TransactionType.valueOf(row.get("type", String.class)),
                row.get("account_id", UUID.class),
                row.get("destination_account_id", UUID.class),
                row.get("category_id", UUID.class),
                row.get("category_name", String.class),
                row.get("amount", BigDecimal.class),
                row.get("currency_code", String.class),
                row.get("amount_base", BigDecimal.class),
                row.get("description", String.class),
                row.get("notes", String.class),
                row.get("occurred_at", OffsetDateTime.class).toInstant(),
                row.get("recurrence_id", UUID.class),
                CuotaDeLaFila.de(row));
    }
}
