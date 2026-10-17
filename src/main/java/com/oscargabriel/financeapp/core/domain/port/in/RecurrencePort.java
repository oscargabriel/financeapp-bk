package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.CreateRecurrenceCommand;
import com.oscargabriel.financeapp.core.domain.model.RecurrenceView;
import com.oscargabriel.financeapp.core.domain.model.UpdateRecurrenceCommand;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RecurrencePort {

    /** Devuelve la serie creada con su proxima ocurrencia. */
    Mono<RecurrenceView> create(UUID userId, CreateRecurrenceCommand alta);

    /** Las series activas, puestas al dia, de la proxima ocurrencia a la mas lejana. */
    Flux<RecurrenceView> list(UUID userId);

    /** Devuelve la serie como quedo. Inexistente, ajena o cancelada dan el mismo 404. */
    Mono<RecurrenceView> update(UUID userId, UUID recurrenceId, UpdateRecurrenceCommand parche);

    /** Inexistente, ajena o ya cancelada dan el mismo 404. */
    Mono<Void> cancel(UUID userId, UUID recurrenceId);
}
