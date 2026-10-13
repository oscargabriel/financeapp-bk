package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;

import reactor.core.publisher.Mono;

public interface UpdateUserProfilePort {

    /**
     * Cambiar el correo exige la contrasena actual. Cambiar la moneda base exige que este activa y que tenga
     * alguna tasa USD→moneda: sin ninguna es 502 (FIN-29).
     */
    Mono<UserProfile> update(UUID userId, UpdateUserProfileCommand command);
}
