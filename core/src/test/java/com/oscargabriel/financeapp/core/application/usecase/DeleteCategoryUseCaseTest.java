package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.core.support.CategoryMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeleteCategoryUseCaseTest {

    @Mock
    private CategoryRepositoryPort categorias;

    @Mock
    private RecurrenceRepositoryPort series;

    @Test
    void borraLaCategoriaDelUsuarioYCompletaVacio() {
        conSeries(0L);
        when(categorias.softDelete(CategoryMother.PLANTAS_ID, CategoryMother.USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase().delete(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID))
                .verifyComplete();

        verify(categorias).softDelete(CategoryMother.PLANTAS_ID, CategoryMother.USER_ID);
    }

    @Test
    void sinFilaBorradaRespondeNotFoundSobreElId() {
        conSeries(0L);
        when(categorias.softDelete(CategoryMother.PLANTAS_ID, CategoryMother.USER_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase().delete(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND))
                .verify();
    }

    @Test
    void rechazaUnaCategoriaConSeriesActivasSinBorrarla() {
        conSeries(1L);

        StepVerifier.create(useCase().delete(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID))
                .expectErrorSatisfies(error -> {
                    esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE);
                    assertThat(((BadRequestException) error).getErrorResponse().getErrors().getFirst().getDescription())
                            .contains("1 serie activa");
                })
                .verify();

        verify(categorias, never()).softDelete(any(), any());
    }

    private void conSeries(long cuantas) {
        when(series.countActiveByCategory(CategoryMother.PLANTAS_ID, CategoryMother.USER_ID))
                .thenReturn(Mono.just(cuantas));
    }

    private static void esError(Throwable error, HttpStatus status, ErrorCodes codigo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors())
                .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                .containsExactly(tuple(codigo.getCode(), "id"));
    }

    private DeleteCategoryUseCase useCase() {
        return new DeleteCategoryUseCase(categorias, series);
    }
}
