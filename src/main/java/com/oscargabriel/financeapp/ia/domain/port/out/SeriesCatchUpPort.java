package com.oscargabriel.financeapp.ia.domain.port.out;

import java.util.UUID;

import reactor.core.publisher.Mono;

/** Registra las ocurrencias vencidas de las series del usuario antes de leer sus movimientos. */
public interface SeriesCatchUpPort {

    Mono<Void> catchUp(UUID userId);
}
