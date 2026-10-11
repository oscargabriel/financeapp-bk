package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * committedCredit es el capital de las cuotas de compras en cuotas que todavia no llegan (FA-108): no
 * cuenta en el saldo vigente, pero ya ocupa cupo. chargesSingleInstallmentInterest es true o false en una
 * CREDIT y null en las demas (FA-134).
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
        Boolean chargesSingleInstallmentInterest,
        boolean active,
        BigDecimal committedCredit) {

    /** Una cuenta sin cuotas por venir y, si es una tarjeta, sin interes en las compras a 1 cuota. */
    public Account(UUID id, String name, AccountType type, String currencyCode, BigDecimal initialBalance,
            BigDecimal currentBalance, BigDecimal creditLimit, Integer statementDay, Integer paymentDueDay,
            BigDecimal monthlyInterestRate, boolean active) {
        this(id, name, type, currencyCode, initialBalance, currentBalance, creditLimit, statementDay, paymentDueDay,
                monthlyInterestRate, type == AccountType.CREDIT ? Boolean.FALSE : null, active, BigDecimal.ZERO);
    }

    /** Un saldo negativo es deuda, asi que sumarlo al limite ya resta lo usado. */
    public BigDecimal availableCredit() {
        if (type != AccountType.CREDIT || creditLimit == null) {
            return null;
        }
        return creditLimit.add(currentBalance).subtract(committedCredit);
    }
}
