package com.oscargabriel.financeapp.domain.model;

import java.time.YearMonth;

/** Rango de meses cerrado por ambos extremos. */
public record MonthRange(YearMonth from, YearMonth to) {

    private static final int MESES_POR_DEFECTO = 12;

    public MonthRange {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "El mes inicial (" + from + ") es posterior al mes final (" + to + ")");
        }
    }

    /**
     * Completa los extremos que el cliente no envio. Sin ninguno de los dos, la ventana por
     * defecto son los doce meses que terminan en el mes actual; con solo uno, el otro se deduce
     * de ese mismo ancho o del mes actual.
     */
    public static MonthRange resolve(YearMonth from, YearMonth to, YearMonth currentMonth) {
        YearMonth fin = to != null ? to : currentMonth;
        YearMonth inicio = from != null ? from : fin.minusMonths(MESES_POR_DEFECTO - 1L);
        return new MonthRange(inicio, fin);
    }
}
