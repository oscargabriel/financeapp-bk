package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;

/** Lo que llego en una moneda distinta a la de la cuenta, antes de convertirse a amount (FA-51). */
public record OriginalAmount(BigDecimal amount, String currencyCode) {
}
