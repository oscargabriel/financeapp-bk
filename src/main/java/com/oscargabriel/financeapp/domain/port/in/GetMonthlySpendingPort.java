package com.oscargabriel.financeapp.domain.port.in;

import java.time.YearMonth;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.MonthlySpending;

import reactor.core.publisher.Flux;

public interface GetMonthlySpendingPort {

    /** from y to admiten null: el caso de uso completa los extremos que falten. */
    Flux<MonthlySpending> get(UUID userId, YearMonth from, YearMonth to);
}
