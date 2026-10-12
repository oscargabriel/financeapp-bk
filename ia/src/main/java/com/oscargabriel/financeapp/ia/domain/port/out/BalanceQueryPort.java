package com.oscargabriel.financeapp.ia.domain.port.out;

import java.time.LocalDate;
import java.util.UUID;

import com.oscargabriel.financeapp.ia.domain.model.AssistantBalance;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Las mismas reglas que GET /api/reports/balance. */
public interface BalanceQueryPort {

    Mono<AssistantBalance.Sums> findSums(UUID userId, LocalDate from, LocalDate to);

    /** Las cuentas activas, con el saldo sin lo programado. */
    Flux<AssistantBalance.AccountBalance> findAccounts(UUID userId);
}
