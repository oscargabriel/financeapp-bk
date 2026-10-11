package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Transaction;

import reactor.core.publisher.Flux;

public interface ListPendingTransactionsPort {

    /** Los pendientes del usuario, del mas reciente al mas antiguo. Ninguno: Flux vacio. */
    Flux<Transaction> listPending(UUID userId);
}
