package com.oscargabriel.financeapp.application.usecase;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.port.in.ListCategoriesPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;

import reactor.core.publisher.Flux;

@Service
public class ListCategoriesUseCase implements ListCategoriesPort {

    private final CategoryQueryPort query;

    public ListCategoriesUseCase(CategoryQueryPort query) {
        this.query = query;
    }

    @Override
    public Flux<Category> list(UUID userId, CategoryScope appliesTo) {
        return Flux.defer(() -> query.findActiveByUser(userId, alcances(appliesTo)));
    }

    private static Set<CategoryScope> alcances(CategoryScope appliesTo) {
        return appliesTo == null ? EnumSet.allOf(CategoryScope.class) : appliesTo.compatibles();
    }
}
