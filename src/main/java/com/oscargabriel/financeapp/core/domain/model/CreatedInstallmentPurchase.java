package com.oscargabriel.financeapp.core.domain.model;

import java.util.List;

/** La compra recien registrada con su plan completo, cada cuota con su movimiento. */
public record CreatedInstallmentPurchase(InstallmentPurchaseView view, List<ScheduledInstallment> installments) {
}