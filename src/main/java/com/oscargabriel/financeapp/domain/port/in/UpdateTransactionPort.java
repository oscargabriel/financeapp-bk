package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.UpdateTransactionCommand;

import reactor.core.publisher.Mono;

public interface UpdateTransactionPort {

    /** Devuelve el movimiento como quedo. Inexistente o de otro usuario: el mismo 404. */
    Mono<Transaction> update(UUID userId, UUID transactionId, UpdateTransactionCommand parche);
}
