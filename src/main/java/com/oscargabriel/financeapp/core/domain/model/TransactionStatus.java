package com.oscargabriel.financeapp.core.domain.model;

/**
 * PENDING es lo que espera que el usuario lo confirme, sea cual sea su origen (FIN-58), y no tiene efecto hasta
 * aprobarse: no mueve saldos ni cuenta en los reportes. Rechazar un pendiente lo borra, por eso no hay un tercer
 * estado.
 */
public enum TransactionStatus {
    PENDING,
    CONFIRMED
}
