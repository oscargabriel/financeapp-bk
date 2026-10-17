package com.oscargabriel.financeapp.core.domain.port.in;

import com.oscargabriel.financeapp.core.domain.model.GoogleRegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.LoginCommand;
import com.oscargabriel.financeapp.core.domain.model.RegisteredUser;
import com.oscargabriel.financeapp.core.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;

import reactor.core.publisher.Mono;

public interface AuthPort {

    /** Valida, crea el usuario y le copia las categorias por defecto. */
    Mono<RegisteredUser> register(RegistrationCommand command);

    Mono<SessionTokens> login(LoginCommand command);

    Mono<SessionTokens> googleRegister(GoogleRegistrationCommand command);

    Mono<SessionTokens> googleLogin(String idToken);

    Mono<SessionTokens> refresh(String refreshToken);

    Mono<Void> logout(String refreshToken);
}
