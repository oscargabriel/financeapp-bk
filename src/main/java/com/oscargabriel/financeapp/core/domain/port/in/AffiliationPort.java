package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.AffiliateBalance;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.model.ShareWithAffiliate;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AffiliationPort {

    /** Las abiertas en las que participa el usuario, de la mas reciente a la mas antigua. */
    Flux<AffiliationView> list(UUID userId);

    /** Las personas afiliadas, y las desvinculadas con partes pendientes, de la afiliacion mas reciente a la mas antigua. */
    Flux<AffiliateBalance> balances(UUID userId);

    /**
     * Las partes entre el usuario y la otra persona de esa afiliacion, de cualquier afiliacion entre los dos. Emite 404
     * si la afiliacion no existe o es ajena, y 409 si no esta ACCEPTED ni UNLINKED.
     */
    Flux<ShareWithAffiliate> shares(UUID userId, UUID affiliationId);

    /** Responde igual tenga o no cuenta el correo: el remitente no sabe cuales estan registrados. */
    Mono<AffiliationView> send(UUID userId, String email);

    /**
     * Las cuatro acciones emiten la relacion como queda. Inexistente o ajena dan 404; una accion que no corresponde al
     * estado o al papel del usuario, 409.
     */
    Mono<AffiliationView> accept(UUID userId, UUID affiliationId);

    Mono<AffiliationView> reject(UUID userId, UUID affiliationId);

    Mono<AffiliationView> cancel(UUID userId, UUID affiliationId);

    Mono<AffiliationView> unlink(UUID userId, UUID affiliationId);
}
