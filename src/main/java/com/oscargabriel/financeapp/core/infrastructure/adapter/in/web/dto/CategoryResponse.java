package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.core.domain.model.Category;

public record CategoryResponse(
        String id,
        String name,
        String appliesTo,
        String icon,
        String color,
        boolean isSystem) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.id().toString(),
                category.name(),
                category.appliesTo().name(),
                category.icon(),
                category.color(),
                category.isSystem());
    }
}
