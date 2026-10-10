package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Una cuota del plan, a medianoche de su dia en la zona del usuario. transactionId es null al simular. */
public record ScheduledInstallment(int number, UUID transactionId, Instant dueAt, BigDecimal principal,
        BigDecimal interest) {

    public BigDecimal amount() {
        return principal.add(interest);
    }
}