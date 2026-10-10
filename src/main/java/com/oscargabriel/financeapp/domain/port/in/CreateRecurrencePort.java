package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.CreateRecurrenceCommand;
import com.oscargabriel.financeapp.domain.model.RecurrenceView;

import reactor.core.publisher.Mono;

public interface CreateRecurrencePort {

    /** Devuelve la serie creada con su proxima ocurrencia. */
    Mono<RecurrenceView> create(UUID userId, CreateRecurrenceCommand alta);
}
