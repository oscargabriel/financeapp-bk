package com.oscargabriel.financeapp.core.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * La parte de un gasto compartido que debe otra persona (FIN-66). amount esta en currencyCode, la moneda en que se
 * registro el gasto. description y occurredAt son copia del gasto: registran lo que se compartio.
 */
public record ExpenseShare(
        UUID id,
        UUID sourceTransactionId,
        UUID payerId,
        UUID debtorId,
        BigDecimal amount,
        String currencyCode,
        String description,
        Instant occurredAt,
        ShareStatus status) {
}
