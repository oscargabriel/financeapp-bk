package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.RecurrenceView;

import reactor.core.publisher.Flux;

public interface ListRecurrencesPort {

    /** Las series activas, puestas al dia, de la proxima ocurrencia a la mas lejana. */
    Flux<RecurrenceView> list(UUID userId);
}
