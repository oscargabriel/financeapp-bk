package com.oscargabriel.financeapp.telegram.domain.port.out;

import java.util.UUID;

import reactor.core.publisher.Mono;

/**
 * Confirmar o descartar desde el chat el grupo que registro el asistente (FIN-61): lo cumple un adapter de puente/.
 * Si el grupo ya no tiene pendientes del usuario, los dos fallan con GrupoYaNoPendienteException.
 */
public interface GruposDelBotPort {

    /** Cuantos movimientos quedaron confirmados. */
    Mono<Integer> confirmar(UUID usuario, UUID grupo);

    Mono<Void> descartar(UUID usuario, UUID grupo);
}
