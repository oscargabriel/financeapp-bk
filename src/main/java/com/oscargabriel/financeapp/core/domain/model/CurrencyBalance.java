package com.oscargabriel.financeapp.core.domain.model;

import java.math.BigDecimal;

/**
 * Lo que dos personas se deben en una moneda (FIN-67), visto por el usuario: owedToMe es la suma de las partes
 * PENDING que la otra persona le debe, iOwe la de las que el le debe a ella. No se convierte entre monedas.
 */
public record CurrencyBalance(String currencyCode, BigDecimal owedToMe, BigDecimal iOwe) {

    public BigDecimal net() {
        return owedToMe.subtract(iOwe);
    }
}
