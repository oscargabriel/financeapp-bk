package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;

/** El parche de una cuenta tal como llega, antes de aplicarse sobre lo guardado. Null no cambia. */
public record UpdateAccountCommand(
        String name,
        String currencyCode,
        BigDecimal initialBalance,
        BigDecimal creditLimit,
        Integer statementDay,
        Integer paymentDueDay) {
}
