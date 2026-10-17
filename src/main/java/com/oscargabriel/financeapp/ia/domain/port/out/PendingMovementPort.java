package com.oscargabriel.financeapp.ia.domain.port.out;

import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.ia.domain.model.AssistantChannel;
import com.oscargabriel.financeapp.ia.domain.model.NewMovement;
import com.oscargabriel.financeapp.ia.domain.model.RegisteredGroup;

import reactor.core.publisher.Mono;

/**
 * Registra sin confirmar los movimientos de un mensaje, con el origen del canal por el que entro: todos en un grupo o
 * ninguno (FIN-58). Lo que el alta rechace sale como BadRequestException 400 con sus errores.
 */
public interface PendingMovementPort {

    Mono<RegisteredGroup> register(UUID userId, AssistantChannel canal, List<NewMovement> movements);
}
