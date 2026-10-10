package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * committedCredit es el capital de las cuotas de compras en cuotas que todavia no llegan (FA-108): no
 * cuenta en el saldo vigente, pero ya ocupa cupo.
 */
public record Account(
        UUID id,
        String name,
        AccountType type,
        String currencyCode,
        BigDecimal initialBalance,
        BigDecimal currentBalance,
        BigDecimal creditLimit,
        Integer statementDay,
        Integer paymentDueDay,
        BigDecimal monthlyInterestRate,
        boolean active,
        BigDecimal committedCredit) {

    /** Una cuenta sin cuotas por venir. */
    public Account(UUID id, String name, AccountType type, String currencyCode, BigDecimal initialBalance,
            BigDecimal currentBalance, BigDecimal creditLimit, Integer statementDay, Integer paymentDueDay,
            BigDecimal monthlyInterestRate, boolean active) {
        this(id, name, type, currencyCode, initialBalance, currentBalance, creditLimit, statementDay, paymentDueDay,
                monthlyInterestRate, active, BigDecimal.ZERO);
    }

    /** Un saldo negativo es deuda, asi que sumarlo al limite ya resta lo usado. */
    public BigDecimal availableCredit() {
        if (type != AccountType.CREDIT || creditLimit == null) {
            return null;
        }
        return creditLimit.add(currentBalance).subtract(committedCredit);
    }
}
