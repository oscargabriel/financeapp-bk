package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface CancelRecurrencePort {

    /** Inexistente, ajena o ya cancelada dan el mismo 404. */
    Mono<Void> cancel(UUID userId, UUID recurrenceId);
}
