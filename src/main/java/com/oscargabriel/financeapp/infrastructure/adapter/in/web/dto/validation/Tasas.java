package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

import java.math.BigDecimal;

/**
 * La tasa de interes mensual de una tarjeta, en porcentaje: de 0 a 10 con hasta 4 decimales, lo que cabe
 * en NUMERIC(6,4) y en ck_accounts_monthly_interest_rate. El tope atrapa una tasa anual escrita en el
 * campo mensual (FA-105).
 */
final class Tasas {

    static final String FUERA_DE_RANGO = "La tasa de interes mensual va de 0 a 10, en porcentaje y con hasta 4 decimales";

    private static final BigDecimal TOPE = BigDecimal.TEN;

    private Tasas() {
    }

    /** Los ceros a la derecha no cuentan como decimales: 1.50000 cabe. */
    static boolean cabe(BigDecimal tasa) {
        return tasa.signum() >= 0
                && tasa.compareTo(TOPE) <= 0
                && tasa.stripTrailingZeros().scale() <= Montos.DECIMALES_MAXIMOS;
    }
}
