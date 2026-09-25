package com.oscargabriel.financeapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategoryScopeTest {

    @Test
    void unGastoAdmiteLasCategoriasDeGastoYLasDeAmbos() {
        assertThat(CategoryScope.EXPENSE.compatibles())
                .containsExactlyInAnyOrder(CategoryScope.EXPENSE, CategoryScope.BOTH);
    }

    @Test
    void unIngresoAdmiteLasCategoriasDeIngresoYLasDeAmbos() {
        assertThat(CategoryScope.INCOME.compatibles())
                .containsExactlyInAnyOrder(CategoryScope.INCOME, CategoryScope.BOTH);
    }

    /** Pedir BOTH es pedir las que sirven para los dos: una de solo gasto no lo cumple. */
    @Test
    void ambosAdmiteSoloLasCategoriasDeAmbos() {
        assertThat(CategoryScope.BOTH.compatibles()).containsExactly(CategoryScope.BOTH);
    }
}
