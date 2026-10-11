package com.oscargabriel.financeapp.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.NewCategory;
import com.oscargabriel.financeapp.domain.model.TransactionType;

import reactor.core.publisher.Mono;

public interface CategoryRepositoryPort {

    /**
     * Inserta la categoria al final de la lista del usuario y la devuelve como quedo en la base. Un
     * nombre que ya usa otra categoria no borrada del usuario sale como BadRequestException con 409.
     */
    Mono<Category> create(NewCategory category);

    /** La categoria no borrada del usuario, o vacio si no existe, esta borrada o es de otro. */
    Mono<Category> findActiveByIdAndUser(UUID categoryId, UUID userId);

    /** Si algun movimiento de ese tipo usa la categoria. */
    Mono<Boolean> hasTransactionsOfType(UUID categoryId, TransactionType type);

    /**
     * Reemplaza nombre, alcance, icono y color de la categoria no borrada del usuario y la devuelve
     * como quedo, o vacio si ya no esta. El nombre repetido sale como en create.
     */
    Mono<Category> update(UUID userId, Category category);

    /** Marca como borrada la categoria viva del usuario; false si no existe, ya estaba borrada o es de otro. */
    Mono<Boolean> softDelete(UUID categoryId, UUID userId);
}
