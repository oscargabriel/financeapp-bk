package com.oscargabriel.financeapp.core.domain.model;

import java.util.EnumSet;
import java.util.Set;

/** Valores de finance.categories.applies_to: para que tipo de movimiento sirve una categoria. */
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

    /**
     * Los alcances a consultar al listar con un filtro opcional: sin filtro son todos. Lo usan los dos
     * listados de categorias, para que filtren igual por construccion.
     */
    public static Set<CategoryScope> compatiblesCon(CategoryScope appliesTo) {
        return appliesTo == null ? EnumSet.allOf(CategoryScope.class) : appliesTo.compatibles();
    }
}
