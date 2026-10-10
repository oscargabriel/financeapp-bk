package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.DayOfWeek;

/** El parche de una serie con su formato ya validado. Null es "no cambia", salvo scope, que siempre va. */
public record UpdateRecurrenceCommand(
        RecurrenceScope scope,
        String accountId,
        String categoryId,
        BigDecimal amount,
        String description,
        Frequency frequency,
        Integer interval,
        DayOfWeek dayOfWeek,
        Integer dayOfMonth) {
}
