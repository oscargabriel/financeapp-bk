package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import com.oscargabriel.financeapp.domain.model.Transaction;

/** occurredAt sale en UTC (Instant): el cliente lo muestra en la zona que quiera. */
public record TransactionResponse(
        String id,
        String type,
        String accountId,
        String destinationAccountId,
        String categoryId,
        BigDecimal amount,
        String currencyCode,
        String description,
        String notes,
        String occurredAt) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.id().toString(),
                t.type().name(),
                t.accountId().toString(),
                t.destinationAccountId() == null ? null : t.destinationAccountId().toString(),
                t.categoryId() == null ? null : t.categoryId().toString(),
                t.amount(),
                t.currencyCode(),
                t.description(),
                t.notes(),
                t.occurredAt().toString());
    }
}
