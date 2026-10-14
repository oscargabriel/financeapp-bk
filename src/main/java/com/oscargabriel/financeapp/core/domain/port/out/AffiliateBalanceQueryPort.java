package com.oscargabriel.financeapp.core.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.AffiliateBalance;
import com.oscargabriel.financeapp.core.domain.model.ShareWithAffiliate;

import reactor.core.publisher.Flux;

public interface AffiliateBalanceQueryPort {

    /** Una entrada por persona, ordenadas por la creacion de su afiliacion, de la mas reciente a la mas antigua. */
    Flux<AffiliateBalance> findBalances(UUID userId);

    /** Las partes en las que uno pago y el otro debe, de la mas reciente a la mas antigua por occurredAt. */
    Flux<ShareWithAffiliate> findSharesBetween(UUID userId, UUID otherUserId);
}
