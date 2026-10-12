package com.oscargabriel.financeapp.ia.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** El movimiento que registro el asistente, con las cuentas y la categoria por nombre (FA-148). */
public record CreatedMovement(
        UUID id,
        TransactionType type,
        String status,
        BigDecimal amount,
        String currencyCode,
        String description,
        Instant occurredAt,
        String account,
        String destinationAccount,
        String category) {
}
