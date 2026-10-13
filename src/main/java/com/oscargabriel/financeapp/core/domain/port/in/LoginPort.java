package com.oscargabriel.financeapp.core.domain.port.in;

import com.oscargabriel.financeapp.core.domain.model.AccessToken;
import com.oscargabriel.financeapp.core.domain.model.LoginCommand;

import reactor.core.publisher.Mono;

public interface LoginPort {

    Mono<AccessToken> login(LoginCommand command);
}
