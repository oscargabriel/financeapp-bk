package com.oscargabriel.financeapp.core.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * La fila que abre una sesion en el login. {@code familyId} agrupa los refresh de la sesion, y
 * {@code expiresAt} es su fin: los refrescos lo copian, no lo extienden.
 */
public record NewRefreshToken(
        UUID id,
        UUID userId,
        UUID familyId,
        String tokenHash,
        long tokenVersion,
        Instant expiresAt,
        Instant createdAt) {
}
