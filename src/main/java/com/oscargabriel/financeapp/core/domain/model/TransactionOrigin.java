package com.oscargabriel.financeapp.core.domain.model;

/**
 * Por donde entro un movimiento. No decide su estado: queda pendiente porque necesita que el usuario lo confirme,
 * sea cual sea su origen (FIN-58).
 */
public enum TransactionOrigin {
    WEB,
    TELEGRAM,
    IMPORT,
    /** Reservado para la ingesta desde correo (FIN-12): hoy ningun endpoint lo produce, solo los datos de demo y de prueba. */
    EMAIL
}
