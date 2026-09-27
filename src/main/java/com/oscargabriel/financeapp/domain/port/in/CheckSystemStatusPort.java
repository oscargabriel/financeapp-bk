package com.oscargabriel.financeapp.domain.port.in;

import com.oscargabriel.financeapp.domain.model.SystemStatus;

import reactor.core.publisher.Mono;

public interface CheckSystemStatusPort {

    Mono<SystemStatus> check();
}
