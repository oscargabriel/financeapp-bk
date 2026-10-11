package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.domain.model.InstallmentRef;

/** La cuota que es un movimiento (FA-108): "3 de 12" de la compra purchaseId. El capital no sale. */
public record InstallmentResponse(String purchaseId, int number, int count) {

    public static InstallmentResponse from(InstallmentRef cuota) {
        return cuota == null
                ? null
                : new InstallmentResponse(cuota.purchaseId().toString(), cuota.number(), cuota.count());
    }
}