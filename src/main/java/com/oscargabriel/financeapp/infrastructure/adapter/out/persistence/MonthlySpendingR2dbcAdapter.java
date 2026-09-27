package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.model.MonthRange;
import com.oscargabriel.financeapp.domain.model.MonthlySpending;
import com.oscargabriel.financeapp.domain.port.out.MonthlySpendingQueryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;

@Component
public class MonthlySpendingR2dbcAdapter implements MonthlySpendingQueryPort {

    private static final String SQL = """
            SELECT period_month,
                   currency_code,
                   total_spent,
                   budget_amount,
                   remaining,
                   percent_used,
                   transaction_count
              FROM finance.v_monthly_spending
             WHERE user_id = :userId
               AND period_month BETWEEN :from AND :to
             ORDER BY period_month DESC
            """;

    private final DatabaseClient databaseClient;

    public MonthlySpendingR2dbcAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<MonthlySpending> findByUserAndRange(UUID userId, MonthRange range) {
        return databaseClient.sql(SQL)
                .bind("userId", userId)
                .bind("from", range.from().atDay(1))
                .bind("to", range.to().atDay(1))
                .map((row, metadata) -> toDomain(row))
                .all();
    }

    private static MonthlySpending toDomain(Row row) {
        return new MonthlySpending(
                YearMonth.from(row.get("period_month", LocalDate.class)),
                row.get("currency_code", String.class),
                row.get("total_spent", BigDecimal.class),
                row.get("budget_amount", BigDecimal.class),
                row.get("remaining", BigDecimal.class),
                row.get("percent_used", BigDecimal.class),
                row.get("transaction_count", Long.class));
    }
}
