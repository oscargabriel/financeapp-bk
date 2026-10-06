package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UpdateCategoryCommand;
import com.oscargabriel.financeapp.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.support.CategoryMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UpdateCategoryUseCaseTest {

    @Mock
    private CategoryRepositoryPort categorias;

    @Captor
    private ArgumentCaptor<Category> categoriaGuardada;

    @Test
    void aplicaNombreIconoYColorNormalizadosYConservaElResto() {
        guardada(CategoryMother.plantasCreada());
        actualizacionPosible();

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID,
                        new UpdateCategoryCommand(" Huerta ", null, " leaf ", " #558b2f ")))
                .expectNextCount(1)
                .verifyComplete();

        verify(categorias).update(eq(CategoryMother.USER_ID), categoriaGuardada.capture());
        assertThat(categoriaGuardada.getValue()).isEqualTo(new Category(CategoryMother.PLANTAS_ID, "Huerta",
                CategoryScope.EXPENSE, "leaf", "#558B2F", false));
    }

    @Test
    void unParcheConTodoEnNullNoCambiaNingunCampo() {
        guardada(CategoryMother.plantasCreada());
        actualizacionPosible();

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID,
                        new UpdateCategoryCommand(null, null, null, "#33691E")))
                .expectNextCount(1)
                .verifyComplete();

        verify(categorias).update(eq(CategoryMother.USER_ID), categoriaGuardada.capture());
        assertThat(categoriaGuardada.getValue()).isEqualTo(new Category(CategoryMother.PLANTAS_ID, "Plantas",
                CategoryScope.EXPENSE, "sprout", "#33691E", false));
    }

    @Test
    void unaCategoriaDeLaSemillaSigueMarcadaComoDeLaSemilla() {
        guardada(CategoryMother.mercado());
        actualizacionPosible();

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.MERCADO_ID,
                        new UpdateCategoryCommand(null, null, null, "#1B5E20")))
                .expectNextCount(1)
                .verifyComplete();

        verify(categorias).update(eq(CategoryMother.USER_ID), categoriaGuardada.capture());
        assertThat(categoriaGuardada.getValue().isSystem()).isTrue();
    }

    @Test
    void devuelveLaCategoriaComoLaDejoLaBase() {
        Category comoQuedo = new Category(CategoryMother.PLANTAS_ID, "Huerta", CategoryScope.EXPENSE, "sprout",
                "#7CB342", false);
        guardada(CategoryMother.plantasCreada());
        when(categorias.update(any(), any())).thenReturn(Mono.just(comoQuedo));

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID,
                        new UpdateCategoryCommand("Huerta", null, null, null)))
                .assertNext(categoria -> assertThat(categoria).isEqualTo(comoQuedo))
                .verifyComplete();
    }

    @Test
    void daNotFoundSobreElIdCuandoLaCategoriaNoEstaEntreLasVivasDelUsuario() {
        when(categorias.findActiveByIdAndUser(CategoryMother.PLANTAS_ID, CategoryMother.USER_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID,
                        new UpdateCategoryCommand("Huerta", null, null, null)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"))
                .verify();

        verify(categorias, never()).update(any(), any());
    }

    @Test
    void rechazaPasarAIngresoUnaCategoriaConGastosSinGuardarNada() {
        guardada(CategoryMother.plantasCreada());
        when(categorias.hasTransactionsOfType(CategoryMother.PLANTAS_ID, TransactionType.EXPENSE))
                .thenReturn(Mono.just(true));

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID,
                        new UpdateCategoryCommand(null, "income", null, "#000000")))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                        "appliesTo"))
                .verify();

        verify(categorias, never()).update(any(), any());
    }

    @Test
    void alPasarAGastoBuscaIngresos() {
        guardada(CategoryMother.salario());
        when(categorias.hasTransactionsOfType(CategoryMother.SALARIO_ID, TransactionType.INCOME))
                .thenReturn(Mono.just(true));

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.SALARIO_ID,
                        new UpdateCategoryCommand(null, "EXPENSE", null, null)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                        "appliesTo"))
                .verify();
    }

    @Test
    void sinMovimientosDelTipoOpuestoPermiteCambiarElAlcance() {
        guardada(CategoryMother.plantasCreada());
        when(categorias.hasTransactionsOfType(CategoryMother.PLANTAS_ID, TransactionType.EXPENSE))
                .thenReturn(Mono.just(false));
        actualizacionPosible();

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID,
                        new UpdateCategoryCommand(null, "INCOME", null, null)))
                .expectNextCount(1)
                .verifyComplete();

        verify(categorias).update(eq(CategoryMother.USER_ID), categoriaGuardada.capture());
        assertThat(categoriaGuardada.getValue().appliesTo()).isEqualTo(CategoryScope.INCOME);
    }

    /** Sin stub de hasTransactionsOfType: el modo estricto de Mockito falla si se llama. */
    @Test
    void pasarAAmbosNoConsultaMovimientos() {
        guardada(CategoryMother.plantasCreada());
        actualizacionPosible();

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID,
                        new UpdateCategoryCommand(null, "BOTH", null, null)))
                .expectNextCount(1)
                .verifyComplete();

        verify(categorias, never()).hasTransactionsOfType(any(), any());
    }

    @Test
    void repetirElMismoAlcanceNoConsultaMovimientos() {
        guardada(CategoryMother.plantasCreada());
        actualizacionPosible();

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID,
                        new UpdateCategoryCommand(null, "expense", null, null)))
                .expectNextCount(1)
                .verifyComplete();

        verify(categorias, never()).hasTransactionsOfType(any(), any());
    }

    @Test
    void propagaElConflictoDeNombreQueReportaElRepositorio() {
        guardada(CategoryMother.plantasCreada());
        when(categorias.update(any(), any())).thenReturn(Mono.error(new BadRequestException(HttpStatus.CONFLICT,
                ErrorCodes.DUPLICATE_RESOURCE, "Ya hay una categoria con ese nombre", "name")));

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID,
                        new UpdateCategoryCommand("Bonos", null, null, null)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                        "name"))
                .verify();
    }

    /** Borrada entre la lectura y la escritura. */
    @Test
    void daNotFoundCuandoElUpdateNoEncuentraLaFila() {
        guardada(CategoryMother.plantasCreada());
        when(categorias.update(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase().update(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID,
                        new UpdateCategoryCommand("Huerta", null, null, null)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"))
                .verify();
    }

    private void guardada(Category categoria) {
        when(categorias.findActiveByIdAndUser(categoria.id(), CategoryMother.USER_ID))
                .thenReturn(Mono.just(categoria));
    }

    private void actualizacionPosible() {
        when(categorias.update(any(), any())).thenAnswer(invocacion -> Mono.just(invocacion.getArgument(1)));
    }

    private static void esError(Throwable error, HttpStatus status, ErrorCodes codigo, String campo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors())
                .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                .containsExactly(tuple(codigo.getCode(), campo));
    }

    private UpdateCategoryUseCase useCase() {
        return new UpdateCategoryUseCase(categorias);
    }
}
