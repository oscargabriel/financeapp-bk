package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Transaction;

import reactor.core.publisher.Mono;

public interface ApprovePendingTransactionPort {

    /**
     * Confirma un pendiente y devuelve el movimiento ya CONFIRMED. Inexistente o de otro usuario: 404;
     * ya confirmado: 409. Con la cuenta origen o destino desactivada: 409 sobre accountId o
     * destinationAccountId, uno por cada cuenta, sin confirmar.
     */
    Mono<Transaction> approve(UUID userId, UUID transactionId);
}
