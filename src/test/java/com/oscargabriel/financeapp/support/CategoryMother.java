package com.oscargabriel.financeapp.support;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;

/** Categorias para los tests. Cada metodo devuelve una fila valida y completa. */
public final class CategoryMother {

    public static final UUID USER_ID = UUID.fromString("10000000-0000-7000-8000-000000000001");

    public static final UUID MERCADO_ID = UUID.fromString("30000000-0000-7000-8000-000000000001");

    private CategoryMother() {
    }

    /** Copiada de la semilla al registrarse. */
    public static Category mercado() {
        return new Category(MERCADO_ID, "Mercado", CategoryScope.EXPENSE, "shopping-cart", "#2E7D32", true);
    }

    public static Category salario() {
        return new Category(UUID.fromString("30000000-0000-7000-8000-000000000002"),
                "Salario", CategoryScope.INCOME, "wallet", "#1B5E20", true);
    }

    /** Creada por el usuario, sin icono ni color: los dos campos son opcionales en la tabla. */
    public static Category propiaSinIconoNiColor() {
        return new Category(UUID.fromString("30000000-0000-7000-8000-000000000003"),
                "Ajustes", CategoryScope.BOTH, null, null, false);
    }
}
