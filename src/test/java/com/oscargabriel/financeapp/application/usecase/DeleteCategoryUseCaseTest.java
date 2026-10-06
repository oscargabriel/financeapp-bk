package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.support.CategoryMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeleteCategoryUseCaseTest {

    @Mock
    private CategoryRepositoryPort categorias;

    @Test
    void borraLaCategoriaDelUsuarioYCompletaVacio() {
        when(categorias.softDelete(CategoryMother.PLANTAS_ID, CategoryMother.USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase().delete(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID))
                .verifyComplete();

        verify(categorias).softDelete(CategoryMother.PLANTAS_ID, CategoryMother.USER_ID);
    }

    @Test
    void sinFilaBorradaRespondeNotFoundSobreElId() {
        when(categorias.softDelete(CategoryMother.PLANTAS_ID, CategoryMother.USER_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase().delete(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                            .containsExactly(tuple(ErrorCodes.NOT_FOUND.getCode(), "id"));
                })
                .verify();
    }

    private DeleteCategoryUseCase useCase() {
        return new DeleteCategoryUseCase(categorias);
    }
}
