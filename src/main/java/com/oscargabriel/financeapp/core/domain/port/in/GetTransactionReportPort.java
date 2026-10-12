package com.oscargabriel.financeapp.core.domain.port.in;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.TransactionReport;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;

import reactor.core.publisher.Mono;

public interface GetTransactionReportPort {

    /** categoryIds, accountIds y types vacios significan "sin filtro". */
    Mono<TransactionReport> get(UUID userId, LocalDate from, LocalDate to, Set<UUID> categoryIds,
            Set<UUID> accountIds, Set<TransactionType> types);
}
