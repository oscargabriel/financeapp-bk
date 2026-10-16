package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.HomeSummary;

import reactor.core.publisher.Mono;

public interface GetHomeSummaryPort {

    /** Siempre el mes en curso en la zona del usuario: la pantalla de Inicio no elige otro. */
    Mono<HomeSummary> get(UUID userId);
}
