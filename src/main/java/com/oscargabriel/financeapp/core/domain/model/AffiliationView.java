package com.oscargabriel.financeapp.core.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Una relacion vista por uno de sus participantes, con los datos de la otra persona. En una enviada
 * que sigue REQUESTED, email es el correo escrito y el nombre va en null aunque tenga cuenta: asi el
 * remitente no sabe que correos estan registrados.
 */
public record AffiliationView(
        UUID id,
        AffiliationStatus status,
        AffiliationDirection direction,
        String email,
        String firstName,
        String lastName,
        Instant createdAt,
        Instant updatedAt) {

    /** La recien enviada, tal como la ve su remitente. */
    public static AffiliationView sent(Affiliation affiliation) {
        return new AffiliationView(affiliation.id(), affiliation.status(), AffiliationDirection.SENT,
                affiliation.addresseeEmail(), null, null, affiliation.createdAt(), affiliation.updatedAt());
    }
}
