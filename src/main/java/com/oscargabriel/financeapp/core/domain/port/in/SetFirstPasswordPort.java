package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.FirstPasswordCommand;

import reactor.core.publisher.Mono;

/** Un usuario sin contrasena define la primera, confirmando su identidad con Google (FIN-53). */
public interface SetFirstPasswordPort {

    Mono<Void> define(UUID userId, FirstPasswordCommand command);
}
