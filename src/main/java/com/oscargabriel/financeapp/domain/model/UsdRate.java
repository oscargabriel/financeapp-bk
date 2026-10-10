package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila USD→currency de exchange_rates: unidades de currency por dolar en rateDate. La pata del
 * propio dolar no tiene fila: vale 1 y no tiene fecha (FA-120).
 */
public record UsdRate(String currency, BigDecimal rate, LocalDate rateDate) {

    public static final String USD = "USD";

    public static UsdRate usd() {
        return new UsdRate(USD, BigDecimal.ONE, null);
    }
}
