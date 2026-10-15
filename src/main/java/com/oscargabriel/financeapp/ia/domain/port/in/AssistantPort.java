package com.oscargabriel.financeapp.ia.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.ia.domain.model.AssistantMessage;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReply;

import reactor.core.publisher.Mono;

public interface AssistantPort {

    /** El usuario sale del token: nada de lo que diga el mensaje ni el modelo lo cambia. */
    Mono<AssistantReply> atender(UUID userId, AssistantMessage mensaje);
}
