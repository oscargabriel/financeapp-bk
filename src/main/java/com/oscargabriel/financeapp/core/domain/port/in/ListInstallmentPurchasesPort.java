package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.InstallmentPurchaseView;

import reactor.core.publisher.Flux;

public interface ListInstallmentPurchasesPort {

    /** Las compras no canceladas con cuotas por venir, por la fecha de la proxima. */
    Flux<InstallmentPurchaseView> list(UUID userId);
}