package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Account(
        UUID id,
        String name,
        AccountType type,
        String currencyCode,
        BigDecimal currentBalance,
        BigDecimal creditLimit,
        boolean active) {

    /** Un saldo negativo es deuda, asi que sumarlo al limite ya resta lo usado. */
    public BigDecimal availableCredit() {
        if (type != AccountType.CREDIT || creditLimit == null) {
            return null;
        }
        return creditLimit.add(currentBalance);
    }
}
