package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * El alta de una serie con su formato ya validado: los valores llegan convertidos. accountId y
 * categoryId siguen como texto porque el caso de uso los resuelve contra las del usuario, y un id
 * mal formado sale alli como "no existe", igual que en el alta de movimientos.
 */
public record CreateRecurrenceCommand(
        TransactionType type,
        String accountId,
        String categoryId,
        BigDecimal amount,
        String description,
        Frequency frequency,
        int interval,
        DayOfWeek dayOfWeek,
        Integer dayOfMonth,
        LocalDate startDate,
        LocalDate endDate,
        Integer occurrences) {
}
