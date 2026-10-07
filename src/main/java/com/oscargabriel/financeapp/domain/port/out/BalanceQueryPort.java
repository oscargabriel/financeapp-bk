package com.oscargabriel.financeapp.domain.port.out;

import java.time.LocalDate;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Balance;

import reactor.core.publisher.Mono;

public interface BalanceQueryPort {

    /** Ingresos y gastos del rango, con el dia cortado en la zona del usuario, y de todos sus movimientos. */
    Mono<Balance.Sums> findSums(UUID userId, LocalDate from, LocalDate to);
}
