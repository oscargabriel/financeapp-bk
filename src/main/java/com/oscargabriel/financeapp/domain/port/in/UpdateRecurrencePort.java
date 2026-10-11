package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.RecurrenceView;
import com.oscargabriel.financeapp.domain.model.UpdateRecurrenceCommand;

import reactor.core.publisher.Mono;

public interface UpdateRecurrencePort {

    /** Devuelve la serie como quedo. Inexistente, ajena o cancelada dan el mismo 404. */
    Mono<RecurrenceView> update(UUID userId, UUID recurrenceId, UpdateRecurrenceCommand parche);
}
