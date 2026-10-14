package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Transaction;

import reactor.core.publisher.Flux;

public interface ApprovePendingGroupPort {

    /**
     * Confirma todo lo que sigue pendiente del grupo, o nada, y lo devuelve CONFIRMED en el orden en que entro (FIN-58).
     * Sin movimientos del usuario en el grupo: 404 sobre groupId; con movimientos pero ninguno pendiente: 409 sobre
     * status. Las reglas de aprobar uno a uno fallan con 409 y el indice del pendiente en el grupo.
     */
    Flux<Transaction> approve(UUID userId, UUID groupId);
}
