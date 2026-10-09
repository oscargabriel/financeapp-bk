package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.oscargabriel.financeapp.domain.model.Transaction;

/**
 * occurredAt sale en UTC (Instant): el cliente lo muestra en la zona que quiera. scheduled se calcula
 * contra el instante de la respuesta: con fecha posterior, el movimiento esta programado (FA-106).
 */
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
        String occurredAt,
        String status,
        String origin,
        boolean scheduled) {

    public static TransactionResponse from(Transaction t, Instant ahora) {
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
                t.occurredAt().toString(),
                t.status().name(),
                t.origin().name(),
                t.scheduledAt(ahora));
    }
}
