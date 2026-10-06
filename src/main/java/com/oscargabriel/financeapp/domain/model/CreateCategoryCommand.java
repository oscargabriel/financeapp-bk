package com.oscargabriel.financeapp.domain.model;

/** Datos crudos del alta de una categoria, tal como llegan del cliente y antes de normalizarse. */
public record CreateCategoryCommand(
        String name,
        String appliesTo,
        String icon,
        String color) {
}
