package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.CreateAccountCommand;

import reactor.core.publisher.Mono;

public interface CreateAccountPort {

    /** La cuenta creada, con el saldo vigente que sembro la base. */
    Mono<Account> create(UUID userId, CreateAccountCommand command);
}
