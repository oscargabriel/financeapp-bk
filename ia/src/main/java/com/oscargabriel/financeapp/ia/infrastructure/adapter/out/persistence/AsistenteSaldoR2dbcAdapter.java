package com.oscargabriel.financeapp.ia.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import io.r2dbc.spi.Row;
import lombok.AllArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.ia.domain.model.AssistantBalance;
import com.oscargabriel.financeapp.ia.domain.port.out.BalanceQueryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Copia las reglas de BalanceR2dbcAdapter y de las cuentas de AccountR2dbcAdapter del core (design.md
 * de FA-148, decision 5). bruno/assistant/ compara el resultado con /reports/balance.
 */
@Component
@AllArgsConstructor
public class AsistenteSaldoR2dbcAdapter implements BalanceQueryPort {

    private static final String SUMAS = """
            SELECT u.base_currency_code,
                   COALESCE(SUM(monto) FILTER (WHERE t.type = 'INCOME' AND t.occurred_at >= desde
                            AND t.occurred_at < hasta), 0) AS period_income,
                   COALESCE(SUM(monto) FILTER (WHERE t.type = 'EXPENSE' AND t.occurred_at >= desde
                            AND t.occurred_at < hasta), 0) AS period_expense,
                   COALESCE(SUM(monto) FILTER (WHERE t.type = 'INCOME'), 0) AS all_income,
                   COALESCE(SUM(monto) FILTER (WHERE t.type = 'EXPENSE'), 0) AS all_expense
              FROM finance.users u
             CROSS JOIN LATERAL (
                   SELECT CAST(:from AS date)::timestamp AT TIME ZONE u.timezone AS desde,
                          (CAST(:to AS date) + 1)::timestamp AT TIME ZONE u.timezone AS hasta) r
              LEFT JOIN finance.transactions t ON t.user_id = u.id AND t.status = 'CONFIRMED'
                    AND t.occurred_at <= now()
             CROSS JOIN LATERAL (
                   SELECT finance.in_currency(u.base_currency_code, t.currency_code, t.amount, t.amount_base,
                                              (t.occurred_at AT TIME ZONE u.timezone)::date) AS monto) m
             WHERE u.id = :userId
             GROUP BY u.base_currency_code
            """;

    /** El saldo sin lo programado, como lo muestra /accounts. */
    private static final String CUENTAS = """
            SELECT name, currency_code,
                   current_balance - finance.scheduled_balance_delta(id) AS current_balance
              FROM finance.accounts
             WHERE user_id = :userId
               AND deleted_at IS NULL
               AND is_active
             ORDER BY lower(name)
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<AssistantBalance.Sums> findSums(UUID userId, LocalDate from, LocalDate to) {
        return databaseClient.sql(SUMAS)
                .bind("userId", userId)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> toSums(row))
                .one();
    }

    @Override
    public Flux<AssistantBalance.AccountBalance> findAccounts(UUID userId) {
        return databaseClient.sql(CUENTAS)
                .bind("userId", userId)
                .map((row, metadata) -> new AssistantBalance.AccountBalance(row.get("name", String.class),
                        row.get("currency_code", String.class), row.get("current_balance", BigDecimal.class)))
                .all();
    }

    private static AssistantBalance.Sums toSums(Row row) {
        return new AssistantBalance.Sums(
                row.get("base_currency_code", String.class),
                row.get("period_income", BigDecimal.class),
                row.get("period_expense", BigDecimal.class),
                row.get("all_income", BigDecimal.class),
                row.get("all_expense", BigDecimal.class));
    }
}
