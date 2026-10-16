package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.CreateAccountCommand;
import com.oscargabriel.financeapp.core.domain.model.UpdateAccountCommand;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AccountPort {

    /** Las borradas nunca salen; las desactivadas, solo con includeInactive. */
    Flux<Account> list(UUID userId, boolean includeInactive);

    /** La cuenta creada, con el saldo vigente que sembro la base. */
    Mono<Account> create(UUID userId, CreateAccountCommand command);

    /** Devuelve la cuenta como quedo. Inexistente, borrada o de otro usuario: el mismo 404. */
    Mono<Account> update(UUID userId, UUID accountId, UpdateAccountCommand parche);

    /**
     * Borrado logico de una cuenta en cero, que se lleva sus movimientos pendientes. Inexistente, ya
     * borrada o de otro usuario: el mismo 404.
     */
    Mono<Void> delete(UUID userId, UUID accountId);
}
