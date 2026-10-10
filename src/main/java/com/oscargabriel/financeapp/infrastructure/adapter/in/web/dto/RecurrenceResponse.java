package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import com.oscargabriel.financeapp.domain.model.Recurrence;
import com.oscargabriel.financeapp.domain.model.RecurrenceView;

/**
 * Una serie (FA-107). Las fechas de la regla son dias (YYYY-MM-DD) y nextOccurrenceAt un instante en
 * UTC, como occurredAt en los movimientos. Los contadores internos no salen: son de la base.
 */
public record RecurrenceResponse(
        String id,
        String type,
        String accountId,
        String categoryId,
        BigDecimal amount,
        String currencyCode,
        String description,
        String frequency,
        int interval,
        String dayOfWeek,
        Integer dayOfMonth,
        String startDate,
        String endDate,
        Integer occurrences,
        String nextOccurrenceAt) {

    public static RecurrenceResponse from(RecurrenceView vista) {
        Recurrence serie = vista.recurrence();
        return new RecurrenceResponse(
                serie.id().toString(),
                serie.type().name(),
                serie.accountId().toString(),
                serie.categoryId().toString(),
                serie.amount(),
                serie.currencyCode(),
                serie.description(),
                serie.rule().frequency().name(),
                serie.rule().interval(),
                serie.rule().dayOfWeek() == null ? null : serie.rule().dayOfWeek().name(),
                serie.rule().dayOfMonth(),
                serie.rule().startDate().toString(),
                serie.endDate() == null ? null : serie.endDate().toString(),
                serie.occurrenceLimit(),
                vista.nextOccurrenceAt() == null ? null : vista.nextOccurrenceAt().toString());
    }
}
