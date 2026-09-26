package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Cuenta ya validada y lista para insertar. No lleva saldo vigente: lo pone el trigger. */
public record NewAccount(
        UUID id,
        UUID userId,
        String name,
        AccountType type,
        String currencyCode,
        BigDecimal initialBalance,
        BigDecimal creditLimit,
        Integer statementDay,
        Integer paymentDueDay) {
}
