package com.oscargabriel.financeapp.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Account;

import reactor.core.publisher.Flux;

public interface AccountQueryPort {

    /** Cuentas no borradas del usuario, las activas primero y luego por nombre. */
    Flux<Account> findByUser(UUID userId, boolean includeInactive);
}
