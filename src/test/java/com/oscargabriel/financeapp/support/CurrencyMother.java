package com.oscargabriel.financeapp.support;

import com.oscargabriel.financeapp.domain.model.Currency;

/** Monedas para los tests, con los datos de la semilla. */
public final class CurrencyMother {

    private CurrencyMother() {
    }

    public static Currency cop() {
        return new Currency("COP", "Peso colombiano", "$");
    }

    public static Currency usd() {
        return new Currency("USD", "Dólar estadounidense", "US$");
    }
}
