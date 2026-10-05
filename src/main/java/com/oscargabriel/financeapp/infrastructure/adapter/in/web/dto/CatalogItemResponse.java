package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.TransactionType;

/**
 * Un valor de enum con su etiqueta en espanol. Los switch no llevan default a proposito: un valor
 * nuevo en el enum no compila hasta que tenga etiqueta.
 */
public record CatalogItemResponse(String code, String description) {

    public static CatalogItemResponse from(AccountType type) {
        return new CatalogItemResponse(type.name(), switch (type) {
            case CASH -> "Efectivo";
            case DEBIT -> "Cuenta débito";
            case CREDIT -> "Tarjeta de crédito";
            case SAVINGS -> "Cuenta de ahorros";
            case INVESTMENT -> "Inversión";
            case OTHER -> "Otra";
        });
    }

    public static CatalogItemResponse from(TransactionType type) {
        return new CatalogItemResponse(type.name(), switch (type) {
            case EXPENSE -> "Gasto";
            case INCOME -> "Ingreso";
            case TRANSFER -> "Transferencia";
        });
    }
}
