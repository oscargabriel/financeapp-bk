package com.oscargabriel.financeapp.core.domain.port.in;

import com.oscargabriel.financeapp.core.domain.model.SystemStatus;

import reactor.core.publisher.Mono;

public interface CheckSystemStatusPort {

    Mono<SystemStatus> check();
}
