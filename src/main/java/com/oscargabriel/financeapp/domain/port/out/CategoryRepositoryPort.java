package com.oscargabriel.financeapp.domain.port.out;

import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.NewCategory;

import reactor.core.publisher.Mono;

public interface CategoryRepositoryPort {

    /**
     * Inserta la categoria al final de la lista del usuario y la devuelve como quedo en la base. Un
     * nombre que ya usa otra categoria no borrada del usuario sale como BadRequestException con 409.
     */
    Mono<Category> create(NewCategory category);
}
