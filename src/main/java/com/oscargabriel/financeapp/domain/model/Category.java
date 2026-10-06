package com.oscargabriel.financeapp.domain.model;

import java.util.UUID;

/** Una fila viva de finance.categories. */
public record Category(
        UUID id,
        String name,
        CategoryScope appliesTo,
        String icon,
        String color,
        boolean isSystem) {
}
