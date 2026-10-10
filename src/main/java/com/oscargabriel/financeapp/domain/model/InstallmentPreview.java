package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Las cuotas que una compra crearia, sin guardar nada. */
public record InstallmentPreview(
        UUID accountId,
        BigDecimal amount,
        String currencyCode,
        LocalDate purchaseDate,
        int installmentCount,
        BigDecimal monthlyInterestRate,
        List<ScheduledInstallment> installments) {

    public BigDecimal totalInterest() {
        return installments.stream().map(ScheduledInstallment::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalAmount() {
        return amount.add(totalInterest());
    }
}