package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import com.oscargabriel.financeapp.domain.model.Account;

public record AccountResponse(
        String id,
        String name,
        String type,
        String currencyCode,
        BigDecimal currentBalance,
        BigDecimal availableCredit,
        boolean isActive) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.id().toString(),
                account.name(),
                account.type().name(),
                account.currencyCode(),
                account.currentBalance(),
                account.availableCredit(),
                account.active());
    }
}
