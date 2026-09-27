package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;

import reactor.core.publisher.Flux;

public interface ListCategoriesPort {

    /** appliesTo admite null: sin filtro devuelve todas las categorias vivas del usuario. */
    Flux<Category> list(UUID userId, CategoryScope appliesTo);
}
