package com.oscargabriel.financeapp.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.GroupScope;
import com.oscargabriel.financeapp.domain.model.InstallmentPurchase;
import com.oscargabriel.financeapp.domain.model.InstallmentPurchaseView;
import com.oscargabriel.financeapp.domain.model.Transaction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InstallmentPurchaseRepositoryPort {

    /** Inserta la compra y sus cuotas en una transaccion: o entra todo o nada. */
    Mono<Void> save(InstallmentPurchase compra, List<Transaction> cuotas);

    /** Las no canceladas del usuario con alguna cuota posterior a ahora, por la fecha de la proxima. */
    Flux<InstallmentPurchaseView> findActiveByUser(UUID userId);

    /** La compra con su resumen; vacio si no existe, es de otro usuario o esta cancelada. */
    Mono<InstallmentPurchaseView> findByIdAndUser(UUID id, UUID userId);

    /**
     * Cambia la descripcion o la categoria (null es "no cambia") de la compra y de sus cuotas del
     * alcance: con FUTURE, las de fecha posterior a ahora; con ALL, todas. false si no habia una compra
     * no cancelada del usuario.
     */
    Mono<Boolean> update(UUID id, UUID userId, GroupScope alcance, Instant ahora, String description,
            UUID categoryId);

    /** Borra las cuotas posteriores a ahora y la marca cancelada; false si no habia una que cancelar. */
    Mono<Boolean> cancel(UUID id, UUID userId, Instant ahora);
}