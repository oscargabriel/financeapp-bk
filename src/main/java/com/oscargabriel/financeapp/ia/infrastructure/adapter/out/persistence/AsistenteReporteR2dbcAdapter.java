package com.oscargabriel.financeapp.ia.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import io.r2dbc.spi.Row;
import lombok.AllArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.ia.domain.model.AssistantReport;
import com.oscargabriel.financeapp.ia.domain.model.ReportFilter;
import com.oscargabriel.financeapp.ia.domain.model.TransactionType;
import com.oscargabriel.financeapp.ia.domain.port.out.ReportQueryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Copia las reglas de TransactionReportR2dbcAdapter del core (design.md de FA-148, decision 5): solo
 * confirmados, rango en dias de la zona del usuario, amount_base convertido con finance.in_currency y
 * del mas reciente al mas antiguo. Ademas deja fuera lo programado, que el core quita en el dominio.
 * bruno/assistant/ compara el resultado con /reports/transactions: si las dos copias se separan, falla.
 */
@Component
@AllArgsConstructor
public class AsistenteReporteR2dbcAdapter implements ReportQueryPort {

    private static final String MONEDA = """
            SELECT base_currency_code FROM finance.users WHERE id = :userId
            """;

    private static final String MOVIMIENTOS = """
            SELECT t.id, t.type, t.amount, t.currency_code,
                   finance.in_currency(u.base_currency_code, t.currency_code, t.amount, t.amount_base,
                                       (t.occurred_at AT TIME ZONE u.timezone)::date) AS amount_base,
                   t.description, t.occurred_at, a.name AS account_name, c.name AS category_name
              FROM finance.transactions t
              JOIN finance.users u ON u.id = t.user_id
              JOIN finance.accounts a ON a.id = t.account_id
              LEFT JOIN finance.categories c ON c.id = t.category_id
             WHERE t.user_id = :userId
               AND t.status = 'CONFIRMED'
               AND t.occurred_at >=(CAST(:from AS date)::timestamp AT TIME ZONE u.timezone)
               AND t.occurred_at < ((CAST(:to AS date) + 1)::timestamp AT TIME ZONE u.timezone)
               AND t.occurred_at <= :asOf
            """;

    private static final String FILTRO_CATEGORIAS = "   AND t.category_id = ANY(:categoryIds)\n";
    /** Origen o destino en el mismo WHERE: una transferencia entre dos cuentas filtradas sale una vez. */
    private static final String FILTRO_CUENTAS =
            "   AND (t.account_id = ANY(:accountIds) OR t.destination_account_id = ANY(:accountIds))\n";
    private static final String FILTRO_TIPOS = "   AND t.type = ANY(:types)\n";
    private static final String ORDEN = " ORDER BY t.occurred_at DESC, t.id DESC";

    private final DatabaseClient databaseClient;

    @Override
    public Mono<String> findBaseCurrency(UUID userId) {
        return databaseClient.sql(MONEDA)
                .bind("userId", userId)
                .map((row, metadata) -> row.get("base_currency_code", String.class))
                .one();
    }

    /** Los filtros entran como fragmentos fijos solo cuando vienen; los valores siempre por bind. */
    @Override
    public Flux<AssistantReport.Item> findMovements(UUID userId, ReportFilter filter, Instant asOf) {
        StringBuilder sql = new StringBuilder(MOVIMIENTOS);
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
                .bind("to", filter.to())
                .bind("asOf", OffsetDateTime.ofInstant(asOf, ZoneOffset.UTC));
        if (!filter.categoryIds().isEmpty()) {
            spec = spec.bind("categoryIds", filter.categoryIds().toArray(UUID[]::new));
        }
        if (!filter.accountIds().isEmpty()) {
            spec = spec.bind("accountIds", filter.accountIds().toArray(UUID[]::new));
        }
        if (!filter.types().isEmpty()) {
            spec = spec.bind("types", filter.types().stream().map(Enum::name).toArray(String[]::new));
        }
        return spec.map((row, metadata) -> toItem(row)).all();
    }

    private static AssistantReport.Item toItem(Row row) {
        return new AssistantReport.Item(
                row.get("id", UUID.class),
                TransactionType.valueOf(row.get("type", String.class)),
                row.get("amount", BigDecimal.class),
                row.get("currency_code", String.class),
                row.get("amount_base", BigDecimal.class),
                row.get("description", String.class),
                row.get("occurred_at", OffsetDateTime.class).toInstant(),
                row.get("account_name", String.class),
                row.get("category_name", String.class));
    }
}
