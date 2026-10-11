package com.oscargabriel.financeapp.support;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateCategoryCommand;

/** Categorias para los tests. Cada metodo devuelve una fila valida y completa. */
public final class CategoryMother {

    public static final UUID USER_ID = UUID.fromString("10000000-0000-7000-8000-000000000001");

    public static final UUID MERCADO_ID = UUID.fromString("30000000-0000-7000-8000-000000000001");

    public static final UUID SALARIO_ID = UUID.fromString("30000000-0000-7000-8000-000000000002");

    public static final UUID PLANTAS_ID = UUID.fromString("30000000-0000-7000-8000-000000000004");

    private CategoryMother() {
    }

    /** Copiada de la semilla al registrarse. */
    public static Category mercado() {
        return new Category(MERCADO_ID, "Mercado", CategoryScope.EXPENSE, "shopping-cart", "#2E7D32", true);
    }

    public static Category salario() {
        return new Category(SALARIO_ID,
                "Salario", CategoryScope.INCOME, "wallet", "#1B5E20", true);
    }

    /** Creada por el usuario y para los dos tipos de movimiento, como la del escenario de pruebas. */
    public static Category ajustes() {
        return new Category(UUID.fromString("30000000-0000-7000-8000-000000000003"),
                "Ajustes", CategoryScope.BOTH, "sliders-horizontal", "#607D8B", false);
    }

    public static CreateCategoryCommand altaPlantas() {
        return new CreateCategoryCommand("Plantas", "EXPENSE", "sprout", "#7CB342");
    }

    /** Como la vuelve a leer el INSERT ... RETURNING. */
    public static Category plantasCreada() {
        return new Category(PLANTAS_ID,
                "Plantas", CategoryScope.EXPENSE, "sprout", "#7CB342", false);
    }
}
