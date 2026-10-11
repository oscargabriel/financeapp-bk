package com.oscargabriel.financeapp.support;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.oscargabriel.financeapp.domain.model.Balance;

/** Sumas de la consulta de saldo. Las cifras son las del escenario de docs/database/test-data.sql. */
public final class BalanceMother {

    public static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    public static final LocalDate HASTA = LocalDate.of(2026, 10, 31);

    private BalanceMother() {
    }

    public static Balance.Totals totales(String ingresos, String gastos) {
        return new Balance.Totals(new BigDecimal(ingresos), new BigDecimal(gastos));
    }

    /** El mes en curso y el historico del usuario del escenario. */
    public static Balance.Sums sumasDelEscenario() {
        return new Balance.Sums("COP", totales("5300000.0000", "1905500.0000"),
                totales("14300000.0000", "5170500.0000"));
    }
}
