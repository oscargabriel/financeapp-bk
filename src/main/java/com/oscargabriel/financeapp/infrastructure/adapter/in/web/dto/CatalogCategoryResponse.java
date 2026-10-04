package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.domain.model.Category;

/** Lo justo para elegir un categoryId; el resto de la categoria lo da GET /categories. */
public record CatalogCategoryResponse(String id, String name) {

    public static CatalogCategoryResponse from(Category category) {
        return new CatalogCategoryResponse(category.id().toString(), category.name());
    }
}
