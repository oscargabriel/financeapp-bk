package com.oscargabriel.financeapp.ia.domain.port.out;

import com.oscargabriel.financeapp.ia.domain.model.AssistantContext;
import com.oscargabriel.financeapp.ia.domain.model.AssistantDecision;
import com.oscargabriel.financeapp.ia.domain.model.AssistantMessage;

import reactor.core.publisher.Mono;

public interface AssistantModelPort {

    /**
     * Nunca completa vacio. Si el modelo no responde, responde un error o algo que no se entiende,
     * emite un BadRequestException 502 EXTERNAL_SERVICE_ERROR.
     */
    Mono<AssistantDecision> interpretar(AssistantMessage mensaje, AssistantContext contexto);
}
