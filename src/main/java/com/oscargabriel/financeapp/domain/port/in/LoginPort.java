package com.oscargabriel.financeapp.domain.port.in;

import com.oscargabriel.financeapp.domain.model.AccessToken;
import com.oscargabriel.financeapp.domain.model.LoginCommand;

import reactor.core.publisher.Mono;

public interface LoginPort {

    Mono<AccessToken> login(LoginCommand command);
}
