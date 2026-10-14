package com.oscargabriel.financeapp.ia.domain.model;

import java.util.List;
import java.util.UUID;

/** Lo que registro el asistente, sin confirmar: el grupo que se confirma o se descarta junto (FIN-58). */
public record CreatedGroup(UUID id, List<CreatedMovement> transactions) {
}
