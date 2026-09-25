package com.oscargabriel.financeapp.domain.port.out;

import java.util.Set;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;

import reactor.core.publisher.Flux;

public interface CategoryQueryPort {

    /** Categorias no borradas del usuario con alguno de los alcances, por sort_order y nombre. */
    Flux<Category> findActiveByUser(UUID userId, Set<CategoryScope> scopes);
}
