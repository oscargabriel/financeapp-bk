package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Category;
import com.oscargabriel.financeapp.core.domain.model.CategoryScope;
import com.oscargabriel.financeapp.core.domain.model.CreateCategoryCommand;
import com.oscargabriel.financeapp.core.domain.model.UpdateCategoryCommand;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoryPort {

    /** appliesTo admite null: sin filtro devuelve todas las categorias vivas del usuario. */
    Flux<Category> list(UUID userId, CategoryScope appliesTo);

    /** La categoria creada, como quedo en la base. */
    Mono<Category> create(UUID userId, CreateCategoryCommand command);

    /** Devuelve la categoria como quedo. Inexistente, borrada o de otro usuario: el mismo 404. */
    Mono<Category> update(UUID userId, UUID categoryId, UpdateCategoryCommand parche);

    /** Borrado logico. Inexistente, ya borrada o de otro usuario: el mismo 404. */
    Mono<Void> delete(UUID userId, UUID categoryId);
}
