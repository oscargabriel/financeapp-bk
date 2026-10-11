package com.oscargabriel.financeapp.domain.model;

import java.util.UUID;

/** Categoria ya normalizada y lista para insertar. El sort_order lo calcula el INSERT. */
public record NewCategory(
        UUID id,
        UUID userId,
        String name,
        CategoryScope appliesTo,
        String icon,
        String color) {
}
