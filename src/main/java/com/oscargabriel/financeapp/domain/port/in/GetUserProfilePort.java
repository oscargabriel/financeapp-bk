package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.UserProfile;

import reactor.core.publisher.Mono;

public interface GetUserProfilePort {

    /** Un usuario desactivado o borrado con el token aun vigente sale como 401, no como vacio. */
    Mono<UserProfile> get(UUID userId);
}
