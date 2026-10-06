package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface DeleteAccountPort {

    /** Borrado logico de una cuenta en cero. Inexistente, ya borrada o de otro usuario: el mismo 404. */
    Mono<Void> delete(UUID userId, UUID accountId);
}
