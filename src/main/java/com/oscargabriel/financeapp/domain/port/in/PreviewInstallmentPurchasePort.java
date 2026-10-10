package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.domain.model.InstallmentPreview;

import reactor.core.publisher.Mono;

public interface PreviewInstallmentPurchasePort {

    /** Valida como el alta y calcula las cuotas sin guardar nada. */
    Mono<InstallmentPreview> preview(UUID userId, CreateInstallmentPurchaseCommand compra);
}