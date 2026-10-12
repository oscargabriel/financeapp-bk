package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.UpdateAccountCommand;

import reactor.core.publisher.Mono;

public interface UpdateAccountPort {

    /** Devuelve la cuenta como quedo. Inexistente, borrada o de otro usuario: el mismo 404. */
    Mono<Account> update(UUID userId, UUID accountId, UpdateAccountCommand parche);
}
