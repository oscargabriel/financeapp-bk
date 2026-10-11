package com.oscargabriel.financeapp.domain.model;

/** El parche de una categoria tal como llega, antes de aplicarse sobre lo guardado. Null no cambia. */
public record UpdateCategoryCommand(
        String name,
        String appliesTo,
        String icon,
        String color) {
}
