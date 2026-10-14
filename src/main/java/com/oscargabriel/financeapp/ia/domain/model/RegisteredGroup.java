package com.oscargabriel.financeapp.ia.domain.model;

import java.util.List;
import java.util.UUID;

/** Lo que registro un mensaje: el grupo de pendientes y sus movimientos, en el orden del mensaje (FIN-58). */
public record RegisteredGroup(UUID groupId, List<RegisteredMovement> movements) {
}
