package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.ChangePasswordCommand;
import com.oscargabriel.financeapp.core.domain.model.FirstPasswordCommand;
import com.oscargabriel.financeapp.core.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;

import reactor.core.publisher.Mono;

public interface UserPort {

    /** Un usuario desactivado o borrado con el token aun vigente sale como 401, no como vacio. */
    Mono<UserProfile> get(UUID userId);

    /**
     * Cambiar el correo exige la contrasena actual. Cambiar la moneda base exige que este activa y que tenga
     * alguna tasa USD→moneda: sin ninguna es 502 (FIN-29).
     */
    Mono<UserProfile> update(UUID userId, UpdateUserProfileCommand command);

    Mono<Void> changePassword(UUID userId, ChangePasswordCommand command);

    /** Un usuario sin contrasena define la primera, confirmando su identidad con Google (FIN-53). */
    Mono<Void> setFirstPassword(UUID userId, FirstPasswordCommand command);
}
