package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Account;

import reactor.core.publisher.Flux;

public interface ListAccountsPort {

    /** Las borradas nunca salen; las desactivadas, solo con includeInactive. */
    Flux<Account> list(UUID userId, boolean includeInactive);
}
