package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.ShareWithAffiliate;

import reactor.core.publisher.Flux;

public interface ListSharesWithAffiliatePort {

    /**
     * Las partes entre el usuario y la otra persona de esa afiliacion, de cualquier afiliacion entre los dos. Emite 404
     * si la afiliacion no existe o es ajena, y 409 si no esta ACCEPTED ni UNLINKED.
     */
    Flux<ShareWithAffiliate> list(UUID userId, UUID affiliationId);
}
