package com.oscargabriel.financeapp.core.domain.port.in;

import com.oscargabriel.financeapp.core.domain.model.RegisteredUser;
import com.oscargabriel.financeapp.core.domain.model.RegistrationCommand;

import reactor.core.publisher.Mono;

public interface RegisterUserPort {

    /** Valida, crea el usuario y le copia las categorias por defecto. */
    Mono<RegisteredUser> register(RegistrationCommand command);
}
