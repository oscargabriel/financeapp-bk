package com.oscargabriel.financeapp.domain.model;

/**
 * Alcance de una edicion en grupo de una serie (FA-107) o de una compra en cuotas (FA-108): solo los
 * movimientos posteriores al momento actual, o todos.
 */
public enum GroupScope {
    FUTURE,
    ALL
}
