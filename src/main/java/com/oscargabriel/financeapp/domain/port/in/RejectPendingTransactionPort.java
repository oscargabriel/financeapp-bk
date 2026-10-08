package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface RejectPendingTransactionPort {

    /** Borra un pendiente. Inexistente o de otro usuario: 404; ya confirmado: 409. */
    Mono<Void> reject(UUID userId, UUID transactionId);
}
