package com.oscargabriel.financeapp.ia.domain.port.out;

import java.time.Instant;
import java.util.UUID;

import com.oscargabriel.financeapp.ia.domain.model.AssistantReport;
import com.oscargabriel.financeapp.ia.domain.model.ReportFilter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReportQueryPort {

    Mono<String> findBaseCurrency(UUID userId);

    /**
     * Los confirmados del rango que ya ocurrieron a asOf, del mas reciente al mas antiguo, con
     * amountBase en la moneda del usuario. Las mismas reglas que GET /api/reports/transactions.
     */
    Flux<AssistantReport.Item> findMovements(UUID userId, ReportFilter filter, Instant asOf);
}
