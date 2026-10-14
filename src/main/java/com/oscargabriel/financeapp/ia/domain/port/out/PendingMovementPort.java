package com.oscargabriel.financeapp.ia.domain.port.out;

import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.ia.domain.model.NewMovement;
import com.oscargabriel.financeapp.ia.domain.model.RegisteredGroup;

import reactor.core.publisher.Mono;

/**
 * Registra sin confirmar, con el origen del asistente, los movimientos de un mensaje: todos en un grupo o ninguno
 * (FIN-58). Lo que el alta rechace sale como BadRequestException 400 con sus errores.
 */
public interface PendingMovementPort {

    Mono<RegisteredGroup> register(UUID userId, List<NewMovement> movements);
}
