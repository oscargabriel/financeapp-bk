package com.oscargabriel.financeapp.core.domain.port.out;

import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.ExpenseShare;
import com.oscargabriel.financeapp.core.domain.model.Transaction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransactionRepositoryPort {

    /** Inserta todos en una sola transaccion de base, en el orden de la lista. */
    Flux<Transaction> saveAll(List<Transaction> transacciones);

    /**
     * Como saveAll, y en la misma transaccion las partes compartidas y los pendientes que reciben otras personas
     * (FIN-66), en ese orden: la parte referencia al movimiento y el pendiente a la parte. Devuelve solo los del
     * usuario.
     */
    Flux<Transaction> saveAll(List<Transaction> transacciones, List<ExpenseShare> partes, List<Transaction> deOtros);

    /** Vacio si no existe o es de otro usuario. */
    Mono<Transaction> findByIdAndUser(UUID id, UUID userId);

    /** Reescribe el movimiento de su usuario; false si ya no existe. */
    Mono<Boolean> update(Transaction transaccion);

    /**
     * false si no existe o es de otro usuario. Los tres borrados arrastran las partes compartidas en la misma
     * transaccion (FIN-66): el pendiente de quien debe deja su parte rechazada, y el gasto de quien pago anula sus
     * partes pendientes y borra los pendientes de esas partes.
     */
    Mono<Boolean> deleteByIdAndUser(UUID id, UUID userId);

    /** Los pendientes del usuario, del mas reciente al mas antiguo. */
    Flux<Transaction> findPendingByUser(UUID userId);

    /** Pasa a CONFIRMED un pendiente del usuario; false si no habia un pendiente que confirmar. */
    Mono<Boolean> confirm(UUID id, UUID userId);

    /**
     * Confirma un pendiente que llego sin cuenta con la cuenta, la categoria y el monto de confirmado (FIN-66);
     * false si ya no habia un pendiente que confirmar.
     */
    Mono<Boolean> confirmWith(Transaction confirmado);

    /**
     * Paga una parte compartida en una transaccion de base (FIN-66): confirma el pendiente como confirmWith, pasa la
     * parte a PAID e inserta el ingreso pendiente de quien pago. false, sin cambiar nada, si el pendiente o la parte
     * ya no estaban pendientes.
     */
    Mono<Boolean> payShare(Transaction confirmado, Transaction ingresoDeQuienPago);

    /** Si el gasto tiene alguna parte compartida que sigue pendiente (FIN-66). */
    Mono<Boolean> hasPendingShares(UUID sourceTransactionId);

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
