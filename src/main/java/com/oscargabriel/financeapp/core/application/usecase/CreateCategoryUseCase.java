package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.Category;
import com.oscargabriel.financeapp.core.domain.model.CategoryScope;
import com.oscargabriel.financeapp.core.domain.model.CreateCategoryCommand;
import com.oscargabriel.financeapp.core.domain.model.NewCategory;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.CreateCategoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class CreateCategoryUseCase implements CreateCategoryPort {

    private final CategoryRepositoryPort categorias;
    private final Clock clock;

    /**
     * El formato ya viene validado por CreateCategoryRequest; aqui queda normalizar. El nombre
     * repetido lo detecta el indice unico de la base y el repositorio lo devuelve como 409.
     */
    @Override
    public Mono<Category> create(UUID userId, CreateCategoryCommand command) {
        return Mono.fromSupplier(() -> nuevaCategoria(userId, command))
                .flatMap(categorias::create);
    }

    private NewCategory nuevaCategoria(UUID userId, CreateCategoryCommand command) {
        return new NewCategory(
                UuidV7.from(clock.instant()),
                userId,
                command.name().trim(),
                CategoryScope.valueOf(command.appliesTo().trim().toUpperCase()),
                command.icon().trim(),
                command.color().trim().toUpperCase());
    }
}
