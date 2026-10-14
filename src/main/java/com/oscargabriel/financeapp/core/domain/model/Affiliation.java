package com.oscargabriel.financeapp.core.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Una solicitud de afiliacion. addresseeId es null si el correo no tenia cuenta al enviarla: nadie
 * la recibe, y solo su remitente participa en ella.
 */
public record Affiliation(
        UUID id,
        UUID requesterId,
        String addresseeEmail,
        UUID addresseeId,
        AffiliationStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public boolean sentBy(UUID userId) {
        return requesterId.equals(userId);
    }

    public boolean receivedBy(UUID userId) {
        return userId.equals(addresseeId);
    }
}
