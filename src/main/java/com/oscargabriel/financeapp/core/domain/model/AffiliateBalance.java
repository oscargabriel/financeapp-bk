package com.oscargabriel.financeapp.core.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Lo que el usuario y otra persona se deben (FIN-67). affiliationId es la afiliacion que la representa: la ACCEPTED,
 * o la UNLINKED mas reciente si no hay. balances trae una entrada por moneda con partes pendientes; vacia, estan a paz
 * y salvo.
 */
public record AffiliateBalance(
        UUID affiliationId,
        AffiliationStatus status,
        Sharing.Person person,
        List<CurrencyBalance> balances) {
}
