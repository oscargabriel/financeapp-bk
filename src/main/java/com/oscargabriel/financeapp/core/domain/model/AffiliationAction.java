package com.oscargabriel.financeapp.core.domain.model;

import java.util.UUID;
import java.util.function.BiPredicate;

/** Las cuatro acciones sobre una relacion: quien la puede hacer, desde que estado y a cual lleva. */
public enum AffiliationAction {

    ACCEPT(Affiliation::receivedBy, AffiliationStatus.REQUESTED, AffiliationStatus.ACCEPTED),
    REJECT(Affiliation::receivedBy, AffiliationStatus.REQUESTED, AffiliationStatus.REJECTED),
    CANCEL(Affiliation::sentBy, AffiliationStatus.REQUESTED, AffiliationStatus.CANCELLED),
    UNLINK((a, userId) -> a.sentBy(userId) || a.receivedBy(userId), AffiliationStatus.ACCEPTED, AffiliationStatus.UNLINKED);

    private final BiPredicate<Affiliation, UUID> role;
    private final AffiliationStatus from;
    private final AffiliationStatus to;

    AffiliationAction(BiPredicate<Affiliation, UUID> role, AffiliationStatus from, AffiliationStatus to) {
        this.role = role;
        this.from = from;
        this.to = to;
    }

    public AffiliationStatus from() {
        return from;
    }

    public AffiliationStatus to() {
        return to;
    }

    public boolean appliesTo(Affiliation affiliation, UUID userId) {
        return affiliation.status() == from && role.test(affiliation, userId);
    }
}
