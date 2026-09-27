package com.oscargabriel.financeapp.domain.port.out;

import java.util.List;

import com.oscargabriel.financeapp.domain.model.Transaction;

import reactor.core.publisher.Flux;

public interface TransactionRepositoryPort {

    /** Inserta todos en una sola transaccion de base, en el orden de la lista. */
    Flux<Transaction> saveAll(List<Transaction> transacciones);
}
