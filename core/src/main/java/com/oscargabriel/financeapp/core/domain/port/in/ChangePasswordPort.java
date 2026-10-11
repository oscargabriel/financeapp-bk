package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.ChangePasswordCommand;

import reactor.core.publisher.Mono;

public interface ChangePasswordPort {

    Mono<Void> change(UUID userId, ChangePasswordCommand command);
}
