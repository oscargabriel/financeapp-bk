package com.oscargabriel.financeapp.domain.model;

/**
 * Resultado del alta. defaultCategories es cuantas filas se copiaron desde default_categories: el
 * usuario sabe con cuantas categorias empieza, y es lo unico observable desde fuera de que la copia
 * ocurrio, mientras no exista GET /api/categories.
 */
public record RegisteredUser(User user, long defaultCategories) {
}
