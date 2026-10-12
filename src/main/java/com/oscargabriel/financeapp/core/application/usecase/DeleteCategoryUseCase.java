package com.oscargabriel.financeapp.core.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;

import com.oscargabriel.financeapp.core.domain.port.in.DeleteCategoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.RecurrenceRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class DeleteCategoryUseCase implements DeleteCategoryPort {

    private final CategoryRepositoryPort categorias;
    private final RecurrenceRepositoryPort series;

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
                                        + UpdateAccountUseCase.seriesActivas(activas)
                                        + ": cancelalas o cambiales la categoria antes de borrarla",
                                "id")))
                .flatMap(borrada -> borrada
                        ? Mono.<Void>empty()
                        : Mono.error(UpdateCategoryUseCase.noEncontrada()));
    }
}
