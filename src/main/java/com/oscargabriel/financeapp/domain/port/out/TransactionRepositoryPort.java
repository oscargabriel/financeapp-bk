package com.oscargabriel.financeapp.domain.port.out;

import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Transaction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransactionRepositoryPort {

    /** Inserta todos en una sola transaccion de base, en el orden de la lista. */
    Flux<Transaction> saveAll(List<Transaction> transacciones);

    /** Vacio si no existe o es de otro usuario. */
    Mono<Transaction> findByIdAndUser(UUID id, UUID userId);

    /** Reescribe el movimiento de su usuario; false si ya no existe. */
    Mono<Boolean> update(Transaction transaccion);

    /** false si no existe o es de otro usuario. */
    Mono<Boolean> deleteByIdAndUser(UUID id, UUID userId);
}
