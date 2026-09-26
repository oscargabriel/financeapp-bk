package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import com.oscargabriel.financeapp.domain.model.CreateAccountCommand;

/**
 * Cuerpo del alta de una cuenta. currentBalance se lee solo para que el caso de uso lo rechace.
 *
 * No valida nada: la validacion entera vive en el caso de uso, que la reporta campo por campo.
 */
public record CreateAccountRequest(
        String name,
        String type,
        String currencyCode,
        BigDecimal initialBalance,
        BigDecimal creditLimit,
        Integer statementDay,
        Integer paymentDueDay,
        BigDecimal currentBalance) {

    public CreateAccountCommand toCommand() {
        return new CreateAccountCommand(name, type, currencyCode, initialBalance, creditLimit,
                statementDay, paymentDueDay, currentBalance);
    }
}
