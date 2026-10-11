package com.oscargabriel.financeapp.domain.model;

/**
 * PENDING lo registra el asistente y no tiene efecto hasta aprobarse: no mueve saldos ni cuenta en
 * los reportes. Rechazar un pendiente lo borra, por eso no hay un tercer estado.
 */
public enum TransactionStatus {
    PENDING,
    CONFIRMED
}
