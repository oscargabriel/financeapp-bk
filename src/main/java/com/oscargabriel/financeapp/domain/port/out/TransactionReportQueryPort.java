package com.oscargabriel.financeapp.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.ReportedTransaction;
import com.oscargabriel.financeapp.domain.model.TransactionReportFilter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransactionReportQueryPort {

    /** La moneda en la que esta amountBase: la base del usuario. */
    Mono<String> findBaseCurrency(UUID userId);

    /** Los movimientos del usuario en el rango y con los filtros, del mas reciente al mas antiguo. */
    Flux<ReportedTransaction> findByUser(UUID userId, TransactionReportFilter filter);
}
