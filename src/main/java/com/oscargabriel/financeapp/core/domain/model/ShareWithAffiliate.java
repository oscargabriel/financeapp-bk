package com.oscargabriel.financeapp.core.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una parte entre el usuario y otra persona, en cualquier estado (FIN-67). description y occurredAt son la copia que
 * guardo la parte al compartirse; resolvedAt es null mientras sigue PENDING.
 */
public record ShareWithAffiliate(
        UUID id,
        String description,
        Instant occurredAt,
        BigDecimal amount,
        String currencyCode,
        ShareStatus status,
        Instant resolvedAt,
        ShareDirection direction) {
}
