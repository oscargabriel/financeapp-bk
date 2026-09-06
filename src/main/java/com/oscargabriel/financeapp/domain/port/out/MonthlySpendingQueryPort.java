package com.oscargabriel.financeapp.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.MonthRange;
import com.oscargabriel.financeapp.domain.model.MonthlySpending;

import reactor.core.publisher.Flux;

public interface MonthlySpendingQueryPort {

    /** Meses del rango con gasto o con meta, del mas reciente al mas antiguo. */
    Flux<MonthlySpending> findByUserAndRange(UUID userId, MonthRange range);
}
