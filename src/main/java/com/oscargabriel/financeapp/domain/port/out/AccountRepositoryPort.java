package com.oscargabriel.financeapp.domain.port.out;

import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.NewAccount;

import reactor.core.publisher.Mono;

public interface AccountRepositoryPort {

    /**
     * Inserta la cuenta y la devuelve como quedo en la base. Un nombre que ya usa otra cuenta no
     * borrada del usuario sale como BadRequestException con 409.
     */
    Mono<Account> create(NewAccount account);
}
