package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.domain.model.UserProfile;

import reactor.core.publisher.Mono;

public interface UpdateUserProfilePort {

    /** Cambiar el correo exige la contrasena actual; la moneda base no se cambia aqui (FA-91). */
    Mono<UserProfile> update(UUID userId, UpdateUserProfileCommand command);
}
