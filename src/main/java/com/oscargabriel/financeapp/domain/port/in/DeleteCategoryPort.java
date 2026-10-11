package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface DeleteCategoryPort {

    /** Borrado logico. Inexistente, ya borrada o de otro usuario: el mismo 404. */
    Mono<Void> delete(UUID userId, UUID categoryId);
}
