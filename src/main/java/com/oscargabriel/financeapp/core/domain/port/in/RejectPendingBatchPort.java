package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.List;
import java.util.UUID;

import reactor.core.publisher.Mono;

public interface RejectPendingBatchPort {

    /**
     * Borra esos pendientes del usuario, todos o ninguno (FIN-68), con el efecto del rechazo individual sobre cada uno.
     * Un id que no es del usuario: 404 en ids[i]; uno confirmado: 409 en ids[i].
     */
    Mono<Void> reject(UUID userId, List<UUID> ids);
}
