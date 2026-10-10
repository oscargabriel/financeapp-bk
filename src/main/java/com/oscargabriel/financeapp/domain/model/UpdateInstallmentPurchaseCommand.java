package com.oscargabriel.financeapp.domain.model;

/** La edicion en grupo de una compra en cuotas: null es "no cambia". */
public record UpdateInstallmentPurchaseCommand(GroupScope scope, String description, String categoryId) {
}