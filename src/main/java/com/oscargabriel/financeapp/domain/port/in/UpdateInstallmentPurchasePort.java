package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.InstallmentPurchaseView;
import com.oscargabriel.financeapp.domain.model.UpdateInstallmentPurchaseCommand;

import reactor.core.publisher.Mono;

public interface UpdateInstallmentPurchasePort {

    /** Aplica el parche a la compra y a sus cuotas del alcance; 404 si no hay compra que editar. */
    Mono<InstallmentPurchaseView> update(UUID userId, UUID purchaseId, UpdateInstallmentPurchaseCommand parche);
}