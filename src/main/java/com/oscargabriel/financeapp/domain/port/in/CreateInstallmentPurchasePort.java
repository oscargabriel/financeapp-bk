package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.domain.model.CreatedInstallmentPurchase;

import reactor.core.publisher.Mono;

public interface CreateInstallmentPurchasePort {

    /** Registra la compra con sus cuotas y la devuelve con el plan. */
    Mono<CreatedInstallmentPurchase> create(UUID userId, CreateInstallmentPurchaseCommand compra);
}