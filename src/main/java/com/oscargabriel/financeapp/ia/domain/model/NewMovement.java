package com.oscargabriel.financeapp.ia.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lo que el asistente pide registrar, con los nombres ya resueltos a ids. occurredAt es un instante
 * ISO-8601 con offset, o null para el instante de la peticion.
 */
public record NewMovement(
        TransactionType type,
        UUID accountId,
        UUID destinationAccountId,
        UUID categoryId,
        BigDecimal amount,
        String currencyCode,
        String description,
        String occurredAt) {
}
