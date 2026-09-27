package com.oscargabriel.financeapp.support;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.MonthlySpending;

/** Filas de v_monthly_spending para los tests. Cada metodo devuelve un mes valido y completo. */
public final class MonthlySpendingMother {

    public static final UUID USER_ID = UUID.fromString("10000000-0000-7000-8000-000000000001");

    private MonthlySpendingMother() {
    }

    /** Mes con meta definida: 1.905.500 gastados sobre una meta de 2.000.000. */
    public static MonthlySpending unMesConMeta(YearMonth mes) {
        return new MonthlySpending(
                mes,
                "COP",
                new BigDecimal("1905500.00"),
                new BigDecimal("2000000.00"),
                new BigDecimal("94500.00"),
                new BigDecimal("95.28"),
                13L);
    }

    /** Mes sin meta: los tres campos que dependen del presupuesto viajan en null. */
    public static MonthlySpending unMesSinMeta(YearMonth mes) {
        return new MonthlySpending(mes, "COP", new BigDecimal("320000.00"), null, null, null, 4L);
    }
}
