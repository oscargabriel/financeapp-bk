package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.AssistantReply;

import reactor.core.publisher.Mono;

public interface AssistantPort {

    /** El usuario sale del token: nada de lo que diga el texto ni el modelo lo cambia. */
    Mono<AssistantReply> atender(UUID userId, String texto);
}
