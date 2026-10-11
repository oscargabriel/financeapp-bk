package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import com.oscargabriel.financeapp.domain.model.Account;

public record AccountResponse(
        String id,
        String name,
        String type,
        String currencyCode,
        BigDecimal initialBalance,
        BigDecimal currentBalance,
        BigDecimal creditLimit,
        BigDecimal availableCredit,
        Integer statementDay,
        Integer paymentDueDay,
        BigDecimal monthlyInterestRate,
        boolean isActive) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.id().toString(),
                account.name(),
                account.type().name(),
                account.currencyCode(),
                account.initialBalance(),
                account.currentBalance(),
                account.creditLimit(),
                account.availableCredit(),
                account.statementDay(),
                account.paymentDueDay(),
                account.monthlyInterestRate(),
                account.active());
    }
}
