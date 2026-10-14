package com.oscargabriel.financeapp.core.domain.port.out;

import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Transaction;

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

    /** Los pendientes del usuario, del mas reciente al mas antiguo. */
    Flux<Transaction> findPendingByUser(UUID userId);

    /** Pasa a CONFIRMED un pendiente del usuario; false si no habia un pendiente que confirmar. */
    Mono<Boolean> confirm(UUID id, UUID userId);

    /** Borra un pendiente del usuario; false si no habia un pendiente que borrar. */
    Mono<Boolean> deletePending(UUID id, UUID userId);

    /** Los movimientos del usuario en el grupo, pendientes o no, en el orden en que entraron (por id). */
    Flux<Transaction> findByGroup(UUID groupId, UUID userId);

    /**
     * Pasa a CONFIRMED todos esos pendientes del usuario en una sola transaccion de base; false, sin confirmar
     * ninguno, si alguno ya no era un pendiente que confirmar.
     */
    Mono<Boolean> confirmAll(List<UUID> ids, UUID userId);

    /** Borra los que sigan pendientes de esos; cuantos borro. */
    Mono<Long> deletePendingIn(List<UUID> ids, UUID userId);
}
