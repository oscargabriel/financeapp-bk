package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

import java.math.BigDecimal;

/** Lo que cabe en las columnas NUMERIC(18,4) de montos: hasta 4 decimales y 14 digitos enteros. */
final class Montos {

    static final int DECIMALES_MAXIMOS = 4;

    static final String FUERA_DE_RANGO = " admite hasta " + DECIMALES_MAXIMOS
            + " decimales y menos de 14 digitos enteros";

    private static final BigDecimal TOPE = new BigDecimal("100000000000000");

    private Montos() {
    }

    /** Los ceros a la derecha no cuentan como decimales: 1.50000 cabe. */
    static boolean cabe(BigDecimal monto) {
        return monto.stripTrailingZeros().scale() <= DECIMALES_MAXIMOS && monto.abs().compareTo(TOPE) < 0;
    }
}
