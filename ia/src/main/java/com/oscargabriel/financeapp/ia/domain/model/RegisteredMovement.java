package com.oscargabriel.financeapp.ia.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Lo que devuelve el registro: el resto del movimiento ya lo conoce el asistente. */
public record RegisteredMovement(UUID id, String status, Instant occurredAt) {
}
