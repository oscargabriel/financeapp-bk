package com.oscargabriel.financeapp.core.domain.port.in;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Balance;
import com.oscargabriel.financeapp.core.domain.model.TransactionReport;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;

import reactor.core.publisher.Mono;

public interface TransactionReportPort {

    /** categoryIds, accountIds y types vacios significan "sin filtro". */
    Mono<TransactionReport> transactions(UUID userId, LocalDate from, LocalDate to, Set<UUID> categoryIds,
            Set<UUID> accountIds, Set<TransactionType> types);

    /** from y to llegan los dos o ninguno; sin ellos, el mes en curso. */
    Mono<Balance> balance(UUID userId, LocalDate from, LocalDate to);
}
