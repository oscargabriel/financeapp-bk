package com.oscargabriel.financeapp.core.domain.port.in;

import reactor.core.publisher.Mono;

public interface LogoutPort {

    Mono<Void> logout(String refreshToken);
}
