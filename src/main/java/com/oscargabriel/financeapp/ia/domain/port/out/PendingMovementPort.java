package com.oscargabriel.financeapp.ia.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.ia.domain.model.NewMovement;
import com.oscargabriel.financeapp.ia.domain.model.RegisteredMovement;

import reactor.core.publisher.Mono;

/**
 * Registra el movimiento pendiente de aprobacion con el origen del asistente. Lo que el alta
 * rechace sale como BadRequestException 400 con sus errores.
 */
public interface PendingMovementPort {

    Mono<RegisteredMovement> register(UUID userId, NewMovement movement);
}
