package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateCategoryCommand;
import com.oscargabriel.financeapp.domain.model.NewCategory;
import com.oscargabriel.financeapp.domain.model.UuidV7;
import com.oscargabriel.financeapp.domain.port.in.CreateCategoryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryRepositoryPort;

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
        String color = opcional(command.color());
        return new NewCategory(
                UuidV7.from(clock.instant()),
                userId,
                command.name().trim(),
                CategoryScope.valueOf(command.appliesTo().trim().toUpperCase()),
                opcional(command.icon()),
                color == null ? null : color.toUpperCase());
    }

    /** Un campo opcional en blanco se guarda como null, no como texto vacio. */
    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
