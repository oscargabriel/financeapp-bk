package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

/**
 * Un movimiento en la moneda de la persona que lo ve (FA-122): su amount si ya esta en esa moneda, y si no su
 * equivalente en USD convertido con la tasa de su fecha local. La regla vive en finance.in_currency, la misma
 * que usan las vistas de gasto mensual. Lo comparten el reporte de movimientos y la consulta de saldo.
 */
final class MontoDeLaPersona {

    /** Sobre el alias t de transactions y u de users. */
    static final String DE_T = """
            finance.in_currency(u.base_currency_code, t.currency_code, t.amount, t.amount_base,
                                (t.occurred_at AT TIME ZONE u.timezone)::date)""";

    private MontoDeLaPersona() {
    }
}
