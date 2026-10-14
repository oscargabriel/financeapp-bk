package com.oscargabriel.financeapp.core.domain.model;

/** Estado de una parte compartida (FIN-16). Mientras esta PENDING es deuda de quien debe con quien pago. */
public enum ShareStatus {
    PENDING,
    PAID,
    REJECTED,
    VOIDED
}
