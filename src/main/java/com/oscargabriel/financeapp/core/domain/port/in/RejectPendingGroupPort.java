package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface RejectPendingGroupPort {

    /**
     * Borra todo lo que sigue pendiente del grupo (FIN-58); lo ya confirmado no cambia. Los mismos 404 y 409 que
     * confirmar el grupo.
     */
    Mono<Void> reject(UUID userId, UUID groupId);
}
