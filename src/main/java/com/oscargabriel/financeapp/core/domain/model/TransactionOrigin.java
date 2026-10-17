package com.oscargabriel.financeapp.core.domain.model;

/**
 * Por donde entro un movimiento. No decide su estado: queda pendiente porque necesita que el usuario lo confirme,
 * sea cual sea su origen (FIN-58).
 */
public enum TransactionOrigin {
    WEB,
    /** Lo pone el bot de Telegram (FIN-61): hoy ningun endpoint lo produce. */
    TELEGRAM,
    IMPORT,
    /** Reservado para la ingesta desde correo (FIN-12): hoy ningun endpoint lo produce, solo los datos de demo y de prueba. */
    EMAIL,
    /** Lo pone el asistente cuando la peticion llega por {@code POST /api/assistant/messages} (FIN-112). */
    WEB_CHAT
}
