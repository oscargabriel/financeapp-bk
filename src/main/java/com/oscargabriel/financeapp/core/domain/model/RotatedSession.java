package com.oscargabriel.financeapp.core.domain.model;

import java.time.Instant;
import java.util.UUID;

/** La sesion de un refresh que se acaba de usar: de quien es, con que version y cuando termina. */
public record RotatedSession(UUID userId, long tokenVersion, Instant expiresAt) {
}
