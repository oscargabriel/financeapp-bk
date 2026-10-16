package com.oscargabriel.financeapp.core.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.core.domain.model.Balance;
import com.oscargabriel.financeapp.core.domain.port.out.BalanceQueryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class BalanceR2dbcAdapter implements BalanceQueryPort {

    /**
     * Una sola pasada para el rango y el historico (design.md de FA-75). El rango se corta igual que en
     * TransactionReportR2dbcAdapter: los limites del dia en la zona del usuario, no la zona aplicada a la
     * columna. El LEFT JOIN deja una fila con ceros a un usuario sin movimientos. El filtro de estado va
     * en el ON y no en el WHERE: un usuario con solo pendientes tambien sale, con ceros (FA-76). Lo
     * programado tampoco entra hasta su fecha (FA-106), y se decide con now() de la base. Las sumas van en la
     * moneda de la persona (FA-122).
     *
     * El total debito sale de v_account_balance_in_base en la misma consulta (FIN-104): la vista convierte
     * cada aporte con la tasa de su fecha, y el filtro de que cuentas suman queda aqui, a la vista. La
     * subconsulta escalar no depende del LEFT JOIN de movimientos, y el COALESCE da 0 a quien no tiene
     * cuentas debito.
     */
    private static final String SQL = """
            SELECT u.base_currency_code,
                   COALESCE((SELECT SUM(v.balance)
                               FROM finance.v_account_balance_in_base v
                              WHERE v.user_id = u.id
                                AND v.account_type = 'DEBIT'
                                AND v.is_active), 0) AS debit_balance,
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
             CROSS JOIN LATERAL (SELECT %s AS monto) m
             WHERE u.id = :userId
             GROUP BY u.id, u.base_currency_code
            """.formatted(MontoDeLaPersona.DE_T);

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Balance.Sums> findSums(UUID userId, LocalDate from, LocalDate to) {
        return databaseClient.sql(SQL)
                .bind("userId", userId)
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> toDomain(row))
                .one();
    }

    private static Balance.Sums toDomain(Row row) {
        return new Balance.Sums(
                row.get("base_currency_code", String.class),
                new Balance.Totals(row.get("period_income", BigDecimal.class),
                        row.get("period_expense", BigDecimal.class)),
                new Balance.Totals(row.get("all_income", BigDecimal.class),
                        row.get("all_expense", BigDecimal.class)),
                row.get("debit_balance", BigDecimal.class));
    }
}
