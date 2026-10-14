package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.AffiliationAction;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;

import reactor.core.publisher.Mono;

public interface ChangeAffiliationPort {

    /**
     * Aplica la accion y emite la relacion como queda. Inexistente o ajena dan 404; una accion que no
     * corresponde al estado o al papel del usuario, 409.
     */
    Mono<AffiliationView> change(UUID userId, UUID affiliationId, AffiliationAction action);
}
