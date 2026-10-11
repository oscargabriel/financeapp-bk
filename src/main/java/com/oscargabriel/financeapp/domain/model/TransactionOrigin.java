package com.oscargabriel.financeapp.domain.model;

/**
 * Por donde entro un movimiento. Decide su estado inicial: lo que interpreta el asistente no tiene
 * efecto hasta que el usuario lo revisa (design.md de FA-77).
 */
public enum TransactionOrigin {
    WEB,
    TELEGRAM,
    IMPORT;

    public TransactionStatus estadoInicial() {
        return this == TELEGRAM ? TransactionStatus.PENDING : TransactionStatus.CONFIRMED;
    }
}
