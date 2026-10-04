package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface DeleteTransactionPort {

    /** Borrado fisico. Inexistente o de otro usuario: el mismo 404. */
    Mono<Void> delete(UUID userId, UUID transactionId);
}
