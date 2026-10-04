package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;

/**
 * El parche de un movimiento tal como llega, antes de validarse contra lo guardado. Un campo en null
 * no cambia. Los ids y la fecha viajan como texto por lo mismo que en CreateTransactionCommand.
 */
public record UpdateTransactionCommand(
        String type,
        String accountId,
        String destinationAccountId,
        String categoryId,
        BigDecimal amount,
        String description,
        String occurredAt) {
}
