package com.oscargabriel.financeapp.domain.port.out;

import com.oscargabriel.financeapp.domain.model.User;
import com.oscargabriel.financeapp.domain.model.UserCredentials;

import reactor.core.publisher.Mono;

public interface UserRepositoryPort {

    Mono<Boolean> existsByEmail(String email);

    /**
     * Credenciales del usuario que puede autenticarse con ese correo, o vacio si no hay ninguno.
     * Un usuario borrado o inactivo cuenta como inexistente: el filtro vive en la consulta para
     * que el caso de uso no tenga forma de distinguir los tres casos ni de olvidarse de uno.
     */
    Mono<UserCredentials> findActiveByEmail(String email);

    /**
     * Inserta el usuario y le copia default_categories en la misma transaccion, y emite cuantas
     * categorias copio. Un usuario sin categorias no es un alta a medias que se pueda arreglar
     * despues: o entran las dos cosas o no entra ninguna.
     */
    Mono<Long> createWithDefaultCategories(User user);
}
