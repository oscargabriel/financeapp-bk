package com.oscargabriel.financeapp.core.domain.model;

/** REQUESTED y ACCEPTED son las abiertas; las otras tres cierran la relacion y conservan su fila (FIN-16). */
public enum AffiliationStatus {
    REQUESTED,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    UNLINKED
}
