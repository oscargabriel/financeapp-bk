package com.oscargabriel.financeapp.domain.port.out;

import com.oscargabriel.financeapp.domain.model.User;

import reactor.core.publisher.Mono;

public interface UserRepositoryPort {

    Mono<Boolean> existsByEmail(String email);

    /**
     * Inserta el usuario y le copia default_categories en la misma transaccion, y emite cuantas
     * categorias copio. Un usuario sin categorias no es un alta a medias que se pueda arreglar
     * despues: o entran las dos cosas o no entra ninguna.
     */
    Mono<Long> createWithDefaultCategories(User user);
}
