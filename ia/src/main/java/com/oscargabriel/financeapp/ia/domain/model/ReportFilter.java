package com.oscargabriel.financeapp.ia.domain.model;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Rango cerrado en dias de la zona del usuario; un filtro vacio no filtra. */
public record ReportFilter(
        LocalDate from,
        LocalDate to,
        Set<UUID> categoryIds,
        Set<UUID> accountIds,
        Set<TransactionType> types) {

    public ReportFilter {
        categoryIds = Set.copyOf(categoryIds);
        accountIds = Set.copyOf(accountIds);
        types = Set.copyOf(types);
    }
}
