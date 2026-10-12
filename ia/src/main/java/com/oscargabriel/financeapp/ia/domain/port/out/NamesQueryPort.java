package com.oscargabriel.financeapp.ia.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.ia.domain.model.UserAccount;
import com.oscargabriel.financeapp.ia.domain.model.UserCategory;

import reactor.core.publisher.Flux;

/** Entre lo que el asistente resuelve nombres: nada borrado, nada desactivado, nada de otro usuario. */
public interface NamesQueryPort {

    /** Ordenadas por nombre sin distinguir mayusculas. */
    Flux<UserAccount> findActiveAccounts(UUID userId);

    /** En el orden en que el usuario las ve. */
    Flux<UserCategory> findLiveCategories(UUID userId);
}
