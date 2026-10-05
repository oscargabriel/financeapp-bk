package com.oscargabriel.financeapp.domain.model;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Rango de dias cerrado por ambos extremos, en la zona del usuario, y los filtros opcionales. Un
 * conjunto vacio significa "sin filtro", no "nada".
 */
public record TransactionReportFilter(
        LocalDate from,
        LocalDate to,
        Set<UUID> categoryIds,
        Set<TransactionType> types) {

    public TransactionReportFilter {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "La fecha inicial (" + from + ") es posterior a la fecha final (" + to + ")");
        }
        categoryIds = categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
        types = types == null ? Set.of() : Set.copyOf(types);
    }

    /** Los tipos que el reporte totaliza, en el orden del enum: todos cuando no hay filtro de tipo. */
    public Set<TransactionType> typesToReport() {
        return types.isEmpty() ? EnumSet.allOf(TransactionType.class) : EnumSet.copyOf(types);
    }
}
