package com.oscargabriel.financeapp.ia.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Valores de finance.categories.applies_to. Copia del enum del core: la IA no ve sus clases
 * (design.md de FA-148, decision 3).
 */
public enum CategoryScope {

    EXPENSE,
    INCOME,
    BOTH;

    /**
     * Alcances que sirven para este: una categoria BOTH vale para un gasto y para un ingreso, pero
     * pedir BOTH es pedir las que sirven para los dos, y una de solo gasto no lo cumple.
     */
    public Set<CategoryScope> compatibles() {
        return this == BOTH ? EnumSet.of(BOTH) : EnumSet.of(this, BOTH);
    }
}
