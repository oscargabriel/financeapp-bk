package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface CancelInstallmentPurchasePort {

    /** Borra las cuotas futuras y cancela la compra; 404 si no hay compra que cancelar. */
    Mono<Void> cancel(UUID userId, UUID purchaseId);
}