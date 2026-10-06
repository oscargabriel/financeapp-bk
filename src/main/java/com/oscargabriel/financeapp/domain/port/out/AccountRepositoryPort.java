package com.oscargabriel.financeapp.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.NewAccount;

import reactor.core.publisher.Mono;

public interface AccountRepositoryPort {

    /**
     * Inserta la cuenta y la devuelve como quedo en la base. Un nombre que ya usa otra cuenta no
     * borrada del usuario sale como BadRequestException con 409.
     */
    Mono<Account> create(NewAccount account);

    /** La cuenta no borrada del usuario, activa o no, o vacio si no existe, esta borrada o es de otro. */
    Mono<Account> findActiveByIdAndUser(UUID accountId, UUID userId);

    /** Si algun movimiento sale de la cuenta o llega a ella. */
    Mono<Boolean> hasTransactions(UUID accountId);

    /**
     * Reemplaza nombre, moneda, saldo inicial, cupo y dias de la cuenta no borrada del usuario y la
     * devuelve como quedo, con el saldo vigente que corrio la base, o vacio si ya no esta. El nombre
     * repetido sale como en create.
     */
    Mono<Account> update(UUID userId, Account account);

    /** Marca como borrada la cuenta viva del usuario; false si no existe, ya estaba borrada o es de otro. */
    Mono<Boolean> softDelete(UUID accountId, UUID userId);
}
