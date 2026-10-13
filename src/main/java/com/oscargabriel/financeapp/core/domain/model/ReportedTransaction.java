package com.oscargabriel.financeapp.core.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un movimiento tal como sale en el reporte: con el nombre de su categoria y con amountBase, el monto
 * en la moneda base del usuario que suman los totales. categoryId y categoryName van en null en las
 * transferencias. destinationAmount es lo que entro al destino de una transferencia entre monedas, en la
 * moneda del destino, y null en todo lo demas (FIN-63).
 */
public record ReportedTransaction(
        UUID id,
        TransactionType type,
        UUID accountId,
        UUID destinationAccountId,
        UUID categoryId,
        String categoryName,
        BigDecimal amount,
        String currencyCode,
        BigDecimal amountBase,
        BigDecimal destinationAmount,
        String description,
        String notes,
        Instant occurredAt,
        UUID recurrenceId,
        InstallmentRef installment) {

    /** Programado: confirmado con fecha posterior al instante dado (FA-106). */
    public boolean scheduledAt(Instant ahora) {
        return occurredAt.isAfter(ahora);
    }
}
