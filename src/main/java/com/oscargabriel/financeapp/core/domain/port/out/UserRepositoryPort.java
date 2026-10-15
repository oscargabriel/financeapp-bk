package com.oscargabriel.financeapp.core.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.User;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;

import reactor.core.publisher.Mono;

public interface UserRepositoryPort {

    Mono<Boolean> existsByEmail(String email);

    /**
     * Credenciales del usuario que puede autenticarse con ese correo, o vacio si no hay ninguno.
     * Un usuario borrado o inactivo cuenta como inexistente: el filtro vive en la consulta para
     * que el caso de uso no tenga forma de distinguir los tres casos ni de olvidarse de uno.
     */
    Mono<UserCredentials> findActiveByEmail(String email);

    /** Credenciales del usuario activo vinculado a esa cuenta Google, o vacio: el mismo filtro (FIN-52). */
    Mono<UserCredentials> findActiveByGoogleSub(String googleSub);

    /** Si la cuenta Google ya es de un usuario no borrado, activo o no. */
    Mono<Boolean> existsByGoogleSub(String googleSub);

    /**
     * Vincula la cuenta Google al usuario activo si todavia no tiene ninguna. FALSE si no vinculo:
     * otra peticion lo vinculo entre medio, o esa cuenta Google ya es de otro usuario.
     */
    Mono<Boolean> linkGoogle(UUID id, String googleSub);

    /**
     * Inserta el usuario y le copia default_categories en la misma transaccion, y emite cuantas
     * categorias copio. Un usuario sin categorias no es un alta a medias que se pueda arreglar
     * despues: o entran las dos cosas o no entra ninguna.
     */
    Mono<Long> createWithDefaultCategories(User user);

    /** El perfil del usuario, o vacio si no existe, esta inactivo o esta borrado: el mismo filtro que el login. */
    Mono<UserProfile> findActiveProfile(UUID id);

    /**
     * Las credenciales del usuario activo, para verificar la contrasena actual o la cuenta Google.
     * Vacio si no esta activo. El hash es nulo si no tiene contrasena (design.md de FIN-53, decision 6).
     */
    Mono<UserCredentials> findActiveCredentials(UUID id);

    /** Si el correo, sin distinguir mayusculas, ya es de otro usuario no borrado. */
    Mono<Boolean> existsByEmailForOtherUser(String email, UUID id);

    /**
     * Guarda nombre, apellido, correo, celular y zona del perfil, y emite el perfil guardado. Un
     * correo que otro tome entre la verificacion y el UPDATE sale como el mismo 409, no como un 500.
     */
    Mono<UserProfile> updateProfile(UserProfile profile);

    Mono<Void> updatePassword(UUID id, String passwordHash);

    /**
     * Guarda la primera contrasena del usuario activo y cierra sus sesiones, solo si todavia no tiene
     * ninguna y esa es su cuenta Google. FALSE si no guardo: ya tenia una, quiza definida entre medio
     * por otra peticion (design.md de FIN-53, decision 7).
     */
    Mono<Boolean> setFirstPassword(UUID id, String googleSub, String passwordHash);
}
