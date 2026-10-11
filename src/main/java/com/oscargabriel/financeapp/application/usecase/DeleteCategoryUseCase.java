package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.port.in.DeleteCategoryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class DeleteCategoryUseCase implements DeleteCategoryPort {

    private final CategoryRepositoryPort categorias;

    /** Un solo UPDATE filtrado por usuario: cero filas es 404, sin una lectura previa. */
    @Override
    public Mono<Void> delete(UUID userId, UUID categoryId) {
        return categorias.softDelete(categoryId, userId)
                .flatMap(borrada -> borrada
                        ? Mono.<Void>empty()
                        : Mono.error(UpdateCategoryUseCase.noEncontrada()));
    }
}
