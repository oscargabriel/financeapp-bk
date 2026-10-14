package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.AffiliateBalance;

import reactor.core.publisher.Flux;

public interface ListAffiliateBalancesPort {

    /** Las personas afiliadas, y las desvinculadas con partes pendientes, de la afiliacion mas reciente a la mas antigua. */
    Flux<AffiliateBalance> list(UUID userId);
}
