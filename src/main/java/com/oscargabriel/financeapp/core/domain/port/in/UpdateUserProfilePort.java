package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;

import reactor.core.publisher.Mono;

public interface UpdateUserProfilePort {

    /** Cambiar el correo exige la contrasena actual; la moneda base no se cambia aqui (FA-91). */
    Mono<UserProfile> update(UUID userId, UpdateUserProfileCommand command);
}
