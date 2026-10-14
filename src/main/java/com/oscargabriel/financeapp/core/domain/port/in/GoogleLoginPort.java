package com.oscargabriel.financeapp.core.domain.port.in;

import com.oscargabriel.financeapp.core.domain.model.SessionTokens;

import reactor.core.publisher.Mono;

public interface GoogleLoginPort {

    Mono<SessionTokens> login(String idToken);
}
