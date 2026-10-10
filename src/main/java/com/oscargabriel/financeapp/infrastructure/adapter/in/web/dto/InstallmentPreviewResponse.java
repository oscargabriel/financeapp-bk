package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;

import com.oscargabriel.financeapp.domain.model.InstallmentPreview;

/** Las cuotas que una compra crearia (FA-108): cada una con su fecha en UTC, capital, interes y total. */
public record InstallmentPreviewResponse(
        String accountId,
        BigDecimal amount,
        String currencyCode,
        String purchaseDate,
        int installmentCount,
        BigDecimal monthlyInterestRate,
        BigDecimal totalInterest,
        BigDecimal totalAmount,
        List<Cuota> installments) {

    public record Cuota(int number, String dueAt, BigDecimal principal, BigDecimal interest, BigDecimal amount) {
    }

    public static InstallmentPreviewResponse from(InstallmentPreview plan) {
        return new InstallmentPreviewResponse(
                plan.accountId().toString(),
                plan.amount(),
                plan.currencyCode(),
                plan.purchaseDate().toString(),
                plan.installmentCount(),
                plan.monthlyInterestRate(),
                plan.totalInterest(),
                plan.totalAmount(),
                plan.installments().stream()
                        .map(c -> new Cuota(c.number(), c.dueAt().toString(), c.principal(), c.interest(), c.amount()))
                        .toList());
    }
}