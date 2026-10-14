package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.ApprovalChoice;
import com.oscargabriel.financeapp.core.domain.model.Transaction;

import reactor.core.publisher.Mono;

public interface ApprovePendingTransactionPort {

    /**
     * Confirma un pendiente y devuelve el movimiento ya CONFIRMED. Inexistente o de otro usuario: 404;
     * ya confirmado: 409. Con la cuenta origen o destino desactivada: 409 sobre accountId o
     * destinationAccountId, uno por cada cuenta, sin confirmar.
     *
     * Un pendiente que llego sin cuenta se aprueba con la cuenta y la categoria de eleccion, que en uno con cuenta
     * sobran (FIN-66). Si es la parte de un gasto compartido, aprobarla es pagarla.
     */
    Mono<Transaction> approve(UUID userId, UUID transactionId, ApprovalChoice eleccion);
}
