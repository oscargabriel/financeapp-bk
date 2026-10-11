package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un movimiento ya validado. destinationAccountId solo en TRANSFER y categoryId solo en EXPENSE e
 * INCOME, como exige ck_transactions_shape. origin no cambia en toda su vida. recurrenceId e installment
 * dicen si es ocurrencia de una serie (FA-107) o cuota de una compra (FA-108); no los elige el cliente.
 * amount esta siempre en currencyCode, la moneda de la cuenta origen. destinationAmount, solo en una
 * transferencia entre monedas distintas, es lo que entra al destino en su moneda; original, lo que llego
 * antes de convertirse (FA-51).
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
        TransactionOrigin origin,
        UUID recurrenceId,
        InstallmentRef installment,
        BigDecimal destinationAmount,
        OriginalAmount original) {

    /** Con fecha posterior al instante dado: no cuenta en saldos ni reportes hasta entonces (FA-106). */
    public boolean scheduledAt(Instant ahora) {
        return occurredAt.isAfter(ahora);
    }
}
