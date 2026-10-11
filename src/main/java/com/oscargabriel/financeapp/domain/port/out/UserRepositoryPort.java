package com.oscargabriel.financeapp.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.User;
import com.oscargabriel.financeapp.domain.model.UserCredentials;
import com.oscargabriel.financeapp.domain.model.UserProfile;

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

    /** El perfil del usuario, o vacio si no existe, esta inactivo o esta borrado: el mismo filtro que el login. */
    Mono<UserProfile> findActiveProfile(UUID id);

    /** El hash del usuario activo, para verificar la contrasena actual. Vacio si no esta activo. */
    Mono<String> findActivePasswordHash(UUID id);

    /** Si el correo, sin distinguir mayusculas, ya es de otro usuario no borrado. */
    Mono<Boolean> existsByEmailForOtherUser(String email, UUID id);

    /**
     * Guarda nombre, apellido, correo, celular y zona del perfil, y emite el perfil guardado. Un
     * correo que otro tome entre la verificacion y el UPDATE sale como el mismo 409, no como un 500.
     */
    Mono<UserProfile> updateProfile(UserProfile profile);

    Mono<Void> updatePassword(UUID id, String passwordHash);
}
