package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;

/**
 * Datos crudos del alta de una cuenta, tal como llegan del cliente y antes de validarse.
 *
 * currentBalance viaja solo para poder rechazarlo: el saldo vigente lo siembra el trigger
 * trg_accounts_seed_balance, y aceptarlo en silencio haria creer al cliente que lo fijo.
 */
public record CreateAccountCommand(
        String name,
        String type,
        String currencyCode,
        BigDecimal initialBalance,
        BigDecimal creditLimit,
        Integer statementDay,
        Integer paymentDueDay,
        BigDecimal currentBalance) {
}
