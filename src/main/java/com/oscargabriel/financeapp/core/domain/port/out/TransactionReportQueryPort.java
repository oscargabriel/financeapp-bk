package com.oscargabriel.financeapp.core.domain.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.ReportedTransaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionReportFilter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransactionReportQueryPort {

    /** La moneda en la que esta amountBase: la base del usuario. */
    Mono<String> findBaseCurrency(UUID userId);

    /** Los movimientos del usuario en el rango y con los filtros, del mas reciente al mas antiguo. */
    Flux<ReportedTransaction> findByUser(UUID userId, TransactionReportFilter filter);

    /** Los ultimos gastos confirmados hasta el instante dado, de cualquier fecha, del mas reciente al mas antiguo. */
    Flux<ReportedTransaction> findLatestExpenses(UUID userId, Instant hasta, int limite);

    /**
     * Los gastos confirmados de los dias dados, en la zona del usuario, y hasta el instante dado, de mayor a menor
     * amountBase; un empate, del mas reciente al mas antiguo.
     */
    Flux<ReportedTransaction> findTopExpenses(UUID userId, LocalDate desde, LocalDate hastaDia, Instant hasta,
            int limite);
}
