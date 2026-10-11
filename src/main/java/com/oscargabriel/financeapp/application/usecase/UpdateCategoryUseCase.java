package com.oscargabriel.financeapp.application.usecase;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UpdateCategoryCommand;
import com.oscargabriel.financeapp.domain.port.in.UpdateCategoryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class UpdateCategoryUseCase implements UpdateCategoryPort {

    private final CategoryRepositoryPort categorias;

    /**
     * El formato del parche ya viene validado por UpdateCategoryRequest, incluido que ningun campo
     * enviado vaya en blanco; aqui queda aplicarlo sobre lo guardado y lo que necesita la base.
     */
    @Override
    public Mono<Category> update(UUID userId, UUID categoryId, UpdateCategoryCommand parche) {
        return categorias.findActiveByIdAndUser(categoryId, userId)
                .switchIfEmpty(Mono.error(UpdateCategoryUseCase::noEncontrada))
                .flatMap(guardada -> {
                    Category resultante = aplicar(guardada, parche);
                    return alcanceCompatible(guardada, resultante.appliesTo())
                            .then(Mono.defer(() -> categorias.update(userId, resultante)));
                })
                .switchIfEmpty(Mono.error(UpdateCategoryUseCase::noEncontrada));
    }

    private static Category aplicar(Category guardada, UpdateCategoryCommand parche) {
        return new Category(
                guardada.id(),
                parche.name() == null ? guardada.name() : parche.name().trim(),
                parche.appliesTo() == null
                        ? guardada.appliesTo()
                        : CategoryScope.valueOf(parche.appliesTo().trim().toUpperCase()),
                parche.icon() == null ? guardada.icon() : parche.icon().trim(),
                parche.color() == null ? guardada.color() : parche.color().trim().toUpperCase(),
                guardada.isSystem());
    }

    /**
     * Pasar a EXPENSE o a INCOME no puede dejar movimientos del otro tipo con una categoria que ya no
     * les sirve. Ampliar a BOTH, o no cambiar el alcance, no lo necesita.
     */
    private Mono<Void> alcanceCompatible(Category guardada, CategoryScope nuevo) {
        if (nuevo == guardada.appliesTo() || nuevo == CategoryScope.BOTH) {
            return Mono.empty();
        }
        TransactionType opuesto = nuevo == CategoryScope.EXPENSE ? TransactionType.INCOME : TransactionType.EXPENSE;
        return categorias.hasTransactionsOfType(guardada.id(), opuesto)
                .filter(tiene -> !tiene)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.CONFLICT,
                        ErrorCodes.RESOURCE_IN_USE,
                        "La categoria tiene movimientos de un tipo que el nuevo alcance no admite", "appliesTo")))
                .then();
    }

    /** Inexistente, borrada y ajena dan lo mismo: distinguirlas confirmaria que el id existe. */
    static BadRequestException noEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "La categoria no existe", "id")));
    }
}
