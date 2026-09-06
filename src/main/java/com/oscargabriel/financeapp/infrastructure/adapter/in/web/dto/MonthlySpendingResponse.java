package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import com.oscargabriel.financeapp.domain.model.MonthlySpending;

public record MonthlySpendingResponse(
        String periodMonth,
        String currencyCode,
        BigDecimal totalSpent,
        BigDecimal budgetAmount,
        BigDecimal remaining,
        BigDecimal percentUsed,
        long transactionCount) {

    public static MonthlySpendingResponse from(MonthlySpending monthlySpending) {
        return new MonthlySpendingResponse(
                monthlySpending.periodMonth().toString(),
                monthlySpending.currencyCode(),
                monthlySpending.totalSpent(),
                monthlySpending.budgetAmount(),
                monthlySpending.remaining(),
                monthlySpending.percentUsed(),
                monthlySpending.transactionCount());
    }
}
