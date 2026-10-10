package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.oscargabriel.financeapp.domain.model.CreatedInstallmentPurchase;
import com.oscargabriel.financeapp.domain.model.InstallmentPurchase;
import com.oscargabriel.financeapp.domain.model.InstallmentPurchaseView;
import com.oscargabriel.financeapp.domain.model.ScheduledInstallment;

/**
 * Una compra en cuotas (FA-108) con el resumen de lo que falta. installments, el plan completo con el
 * movimiento de cada cuota, solo sale en el alta: en el listado y la edicion cada cuota ya esta en
 * reports/transactions con su installment.
 */
public record InstallmentPurchaseResponse(
        String id,
        String accountId,
        String categoryId,
        BigDecimal amount,
        String currencyCode,
        String description,
        String purchaseDate,
        int installmentCount,
        BigDecimal monthlyInterestRate,
        int paidCount,
        BigDecimal remainingPrincipal,
        BigDecimal remainingAmount,
        Next nextInstallment,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<Cuota> installments) {

    public record Next(int number, String transactionId, String dueAt, BigDecimal amount) {
    }

    public record Cuota(int number, String transactionId, String dueAt, BigDecimal principal, BigDecimal interest,
            BigDecimal amount) {

        static Cuota from(ScheduledInstallment cuota) {
            return new Cuota(cuota.number(), cuota.transactionId().toString(), cuota.dueAt().toString(),
                    cuota.principal(), cuota.interest(), cuota.amount());
        }
    }

    public static InstallmentPurchaseResponse from(InstallmentPurchaseView vista) {
        return de(vista, null);
    }

    public static InstallmentPurchaseResponse from(CreatedInstallmentPurchase creada) {
        return de(creada.view(), creada.installments().stream().map(Cuota::from).toList());
    }

    private static InstallmentPurchaseResponse de(InstallmentPurchaseView vista, List<Cuota> plan) {
        InstallmentPurchase compra = vista.purchase();
        InstallmentPurchaseView.NextInstallment proxima = vista.nextInstallment();
        return new InstallmentPurchaseResponse(
                compra.id().toString(),
                compra.accountId().toString(),
                compra.categoryId().toString(),
                compra.amount(),
                compra.currencyCode(),
                compra.description(),
                compra.purchaseDate().toString(),
                compra.installmentCount(),
                compra.monthlyInterestRate(),
                vista.paidCount(),
                vista.remainingPrincipal(),
                vista.remainingAmount(),
                proxima == null ? null : new Next(proxima.number(), proxima.transactionId().toString(),
                        proxima.dueAt().toString(), proxima.amount()),
                plan);
    }
}