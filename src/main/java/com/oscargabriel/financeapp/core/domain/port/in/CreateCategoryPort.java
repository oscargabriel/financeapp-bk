package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Category;
import com.oscargabriel.financeapp.core.domain.model.CreateCategoryCommand;

import reactor.core.publisher.Mono;

public interface CreateCategoryPort {

    /** La categoria creada, como quedo en la base. */
    Mono<Category> create(UUID userId, CreateCategoryCommand command);
}
