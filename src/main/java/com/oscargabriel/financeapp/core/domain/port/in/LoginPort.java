package com.oscargabriel.financeapp.core.domain.port.in;

import com.oscargabriel.financeapp.core.domain.model.LoginCommand;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;

import reactor.core.publisher.Mono;

public interface LoginPort {

    Mono<SessionTokens> login(LoginCommand command);
}
