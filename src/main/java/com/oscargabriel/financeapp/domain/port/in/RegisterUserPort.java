package com.oscargabriel.financeapp.domain.port.in;

import com.oscargabriel.financeapp.domain.model.RegisteredUser;
import com.oscargabriel.financeapp.domain.model.RegistrationCommand;

import reactor.core.publisher.Mono;

public interface RegisterUserPort {

    /** Valida, crea el usuario y le copia las categorias por defecto. */
    Mono<RegisteredUser> register(RegistrationCommand command);
}
