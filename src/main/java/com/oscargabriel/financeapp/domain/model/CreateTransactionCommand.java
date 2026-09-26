package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;

/**
 * Un elemento del lote tal como llega del cliente, antes de validarse. Los ids y la fecha viajan
 * como texto para que un valor mal formado salga como error del campo con su indice, y no como un
 * JSON_PARSING_ERROR sobre el cuerpo entero.
 *
 * destinationAmount viaja solo para poder rechazarlo mientras todo sea COP.
 */
public record CreateTransactionCommand(
        String type,
        String accountId,
        String destinationAccountId,
        String categoryId,
        BigDecimal amount,
        BigDecimal destinationAmount,
        String currencyCode,
        String description,
        String notes,
        String occurredAt) {
}
