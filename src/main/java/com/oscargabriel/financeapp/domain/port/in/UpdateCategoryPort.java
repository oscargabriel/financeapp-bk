package com.oscargabriel.financeapp.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.UpdateCategoryCommand;

import reactor.core.publisher.Mono;

public interface UpdateCategoryPort {

    /** Devuelve la categoria como quedo. Inexistente, borrada o de otro usuario: el mismo 404. */
    Mono<Category> update(UUID userId, UUID categoryId, UpdateCategoryCommand parche);
}
