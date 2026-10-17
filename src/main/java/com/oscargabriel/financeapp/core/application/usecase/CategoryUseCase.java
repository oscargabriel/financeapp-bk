package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Category;
import com.oscargabriel.financeapp.core.domain.model.CategoryScope;
import com.oscargabriel.financeapp.core.domain.model.CreateCategoryCommand;
import com.oscargabriel.financeapp.core.domain.model.NewCategory;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.model.UpdateCategoryCommand;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.CategoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.RecurrenceRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class CategoryUseCase implements CategoryPort {

    private final CategoryQueryPort query;
    private final CategoryRepositoryPort categorias;
    private final RecurrenceRepositoryPort series;
    private final Clock clock;

    @Override
    public Flux<Category> list(UUID userId, CategoryScope appliesTo) {
        return Flux.defer(() -> query.findActiveByUser(userId, CategoryScope.compatiblesCon(appliesTo)));
    }

    /**
     * El formato ya viene validado por CreateCategoryRequest; aqui queda normalizar. El nombre
     * repetido lo detecta el indice unico de la base y el repositorio lo devuelve como 409.
     */
    @Override
    public Mono<Category> create(UUID userId, CreateCategoryCommand command) {
        return Mono.fromSupplier(() -> nuevaCategoria(userId, command))
                .flatMap(categorias::create);
    }

    /**
     * El formato del parche ya viene validado por UpdateCategoryRequest, incluido que ningun campo
     * enviado vaya en blanco; aqui queda aplicarlo sobre lo guardado y lo que necesita la base.
     */
    @Override
    public Mono<Category> update(UUID userId, UUID categoryId, UpdateCategoryCommand parche) {
        return categorias.findActiveByIdAndUser(categoryId, userId)
                .switchIfEmpty(Mono.error(CategoryUseCase::noEncontrada))
                .flatMap(guardada -> {
                    Category resultante = aplicar(guardada, parche);
                    return alcanceCompatible(guardada, resultante.appliesTo())
                            .then(Mono.defer(() -> categorias.update(userId, resultante)));
                })
                .switchIfEmpty(Mono.error(CategoryUseCase::noEncontrada));
    }

    /**
     * Un UPDATE filtrado por usuario: cero filas es 404, sin leer la categoria antes. Lo unico que se consulta
     * antes son sus series activas, que seguirian creando ocurrencias con la categoria borrada (FA-116). El
     * conteo solo ve categorias vivas del usuario, asi que una ajena o ya borrada llega al UPDATE y da 404.
     */
    @Override
    public Mono<Void> delete(UUID userId, UUID categoryId) {
        return series.countActiveByCategory(categoryId, userId)
                .flatMap(activas -> activas == 0
                        ? categorias.softDelete(categoryId, userId)
                        : Mono.error(new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                                "La categoria la usa" + (activas == 1 ? " " : "n ")
                                        + AccountUseCase.seriesActivas(activas)
                                        + ": cancelalas o cambiales la categoria antes de borrarla",
                                "id")))
                .flatMap(borrada -> borrada
                        ? Mono.<Void>empty()
                        : Mono.error(noEncontrada()));
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
    private static BadRequestException noEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "La categoria no existe", "id")));
    }
}
