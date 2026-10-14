package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.AffiliationView;

import reactor.core.publisher.Flux;

public interface ListAffiliationsPort {

    /** Las abiertas en las que participa el usuario, de la mas reciente a la mas antigua. */
    Flux<AffiliationView> list(UUID userId);
}
