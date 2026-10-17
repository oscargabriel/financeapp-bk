package com.oscargabriel.financeapp.ia.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.ia.domain.model.AssistantChannel;
import com.oscargabriel.financeapp.ia.domain.model.AssistantMessage;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReply;

import reactor.core.publisher.Mono;

public interface AssistantPort {

    /**
     * El usuario sale del token, y el canal, como el usuario, lo fija quien llama segun por donde entro la peticion:
     * nada de lo que diga el mensaje ni el modelo los cambia.
     */
    Mono<AssistantReply> atender(UUID userId, AssistantChannel canal, AssistantMessage mensaje);
}
