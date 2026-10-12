package com.oscargabriel.financeapp.core.application.usecase;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.Category;
import com.oscargabriel.financeapp.core.domain.model.CategoryScope;
import com.oscargabriel.financeapp.core.domain.port.in.ListCategoriesPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;

import reactor.core.publisher.Flux;

@Service
@AllArgsConstructor
public class ListCategoriesUseCase implements ListCategoriesPort {

    private final CategoryQueryPort query;

    @Override
    public Flux<Category> list(UUID userId, CategoryScope appliesTo) {
        return Flux.defer(() -> query.findActiveByUser(userId, alcances(appliesTo)));
    }

    private static Set<CategoryScope> alcances(CategoryScope appliesTo) {
        return appliesTo == null ? EnumSet.allOf(CategoryScope.class) : appliesTo.compatibles();
    }
}
