package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.core.domain.model.CreatedInstallmentPurchase;
import com.oscargabriel.financeapp.core.domain.model.InstallmentPreview;
import com.oscargabriel.financeapp.core.domain.model.InstallmentPurchaseView;
import com.oscargabriel.financeapp.core.domain.model.UpdateInstallmentPurchaseCommand;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InstallmentPurchasePort {

    /** Valida como el alta y calcula las cuotas sin guardar nada. */
    Mono<InstallmentPreview> preview(UUID userId, CreateInstallmentPurchaseCommand compra);

    /** Registra la compra con sus cuotas y la devuelve con el plan. */
    Mono<CreatedInstallmentPurchase> create(UUID userId, CreateInstallmentPurchaseCommand compra);

    /** Las compras no canceladas con cuotas por venir, por la fecha de la proxima. */
    Flux<InstallmentPurchaseView> list(UUID userId);

    /** Aplica el parche a la compra y a sus cuotas del alcance; 404 si no hay compra que editar. */
    Mono<InstallmentPurchaseView> update(UUID userId, UUID purchaseId, UpdateInstallmentPurchaseCommand parche);

    /** Borra las cuotas futuras y cancela la compra; 404 si no hay compra que cancelar. */
    Mono<Void> cancel(UUID userId, UUID purchaseId);
}
