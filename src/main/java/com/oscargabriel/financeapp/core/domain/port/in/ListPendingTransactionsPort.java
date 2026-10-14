package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.Optional;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Transaction;

import reactor.core.publisher.Flux;

public interface ListPendingTransactionsPort {

    /**
     * Los pendientes del usuario, del mas reciente al mas antiguo. Con sharedBy, solo los que compartio la persona de
     * ese correo (FIN-68). Ninguno: Flux vacio.
     */
    Flux<Transaction> listPending(UUID userId, Optional<String> sharedBy);
}
