package com.oscargabriel.financeapp.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Balance;

import reactor.core.publisher.Mono;

public interface GetBalancePort {

    /** from y to llegan los dos o ninguno; sin ellos, el mes en curso. */
    Mono<Balance> get(UUID userId, LocalDate from, LocalDate to);
}
