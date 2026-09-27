package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;

/**
 * Un elemento del lote. Los ids y occurredAt llegan como texto para que el caso de uso los reporte
 * con su indice; destinationAmount, solo para poder rechazarlo.
 *
 * No valida nada: la validacion entera vive en el caso de uso.
 */
public record CreateTransactionRequest(
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

    public CreateTransactionCommand toCommand() {
        return new CreateTransactionCommand(type, accountId, destinationAccountId, categoryId, amount,
                destinationAmount, currencyCode, description, notes, occurredAt);
    }
}
