package com.oscargabriel.financeapp.core.domain.model;

/**
 * Por donde entro un movimiento. No decide su estado: queda pendiente porque necesita que el usuario lo confirme,
 * sea cual sea su origen (FIN-58).
 */
public enum TransactionOrigin {
    WEB,
    TELEGRAM,
    IMPORT
}
