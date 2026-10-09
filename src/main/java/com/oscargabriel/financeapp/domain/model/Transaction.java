package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un movimiento ya validado. destinationAccountId solo en TRANSFER y categoryId solo en EXPENSE e
 * INCOME, como exige ck_transactions_shape. origin no cambia en toda su vida.
 */
public record Transaction(
        UUID id,
        UUID userId,
        TransactionType type,
        UUID accountId,
        UUID destinationAccountId,
        UUID categoryId,
        BigDecimal amount,
        String currencyCode,
        String description,
        String notes,
        Instant occurredAt,
        TransactionStatus status,
        TransactionOrigin origin) {

    /** Con fecha posterior al instante dado: no cuenta en saldos ni reportes hasta entonces (FA-106). */
    public boolean scheduledAt(Instant ahora) {
        return occurredAt.isAfter(ahora);
    }
}
