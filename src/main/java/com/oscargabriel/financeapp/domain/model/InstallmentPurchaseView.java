package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una compra con el resumen de sus cuotas posteriores al momento actual: las que existen, no las del
 * plan, asi que una borrada a mano cuenta como pagada y una editada aporta su monto nuevo.
 */
public record InstallmentPurchaseView(
        InstallmentPurchase purchase,
        int paidCount,
        BigDecimal remainingPrincipal,
        BigDecimal remainingAmount,
        NextInstallment nextInstallment) {

    /** La cuota existente mas proxima; null si ya no queda ninguna. */
    public record NextInstallment(int number, UUID transactionId, Instant dueAt, BigDecimal amount) {
    }
}