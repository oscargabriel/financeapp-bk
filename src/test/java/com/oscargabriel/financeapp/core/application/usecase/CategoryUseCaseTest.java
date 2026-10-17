package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Category;
import com.oscargabriel.financeapp.core.domain.model.CategoryScope;
import com.oscargabriel.financeapp.core.domain.model.CreateCategoryCommand;
import com.oscargabriel.financeapp.core.domain.model.NewCategory;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.model.UpdateCategoryCommand;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.core.support.CategoryMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CategoryUseCaseTest {

    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-10-05T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private CategoryQueryPort query;

    @Mock
    private CategoryRepositoryPort categorias;

    @Mock
    private RecurrenceRepositoryPort series;

    @Captor
    private ArgumentCaptor<Set<CategoryScope>> alcancesCapturados;

    @Captor
    private ArgumentCaptor<NewCategory> categoriaCreada;

    @Captor
    private ArgumentCaptor<Category> categoriaGuardada;

    @Nested
    class Listar {

        @Test
        void sinFiltroConsultaLosTresAlcances() {
            assertThat(alcancesConsultadosPara(null))
                    .containsExactlyInAnyOrder(CategoryScope.EXPENSE, CategoryScope.INCOME, CategoryScope.BOTH);
        }

        @Test
        void conFiltroDeGastoConsultaGastoYAmbos() {
            assertThat(alcancesConsultadosPara(CategoryScope.EXPENSE))
                    .containsExactlyInAnyOrder(CategoryScope.EXPENSE, CategoryScope.BOTH);
        }

        @Test
        void conFiltroDeIngresoConsultaIngresoYAmbos() {
            assertThat(alcancesConsultadosPara(CategoryScope.INCOME))
                    .containsExactlyInAnyOrder(CategoryScope.INCOME, CategoryScope.BOTH);
        }

        @Test
        void conFiltroDeAmbosConsultaSoloAmbos() {
            assertThat(alcancesConsultadosPara(CategoryScope.BOTH)).containsExactly(CategoryScope.BOTH);
        }

        @Test
        void emiteLasCategoriasEnElOrdenQueEntregaElPuerto() {
            when(query.findActiveByUser(eq(CategoryMother.USER_ID), any())).thenReturn(
                    Flux.just(CategoryMother.mercado(), CategoryMother.salario()));

            StepVerifier.create(useCase().list(CategoryMother.USER_ID, null))
                    .assertNext(categoria -> assertThat(categoria.name()).isEqualTo("Mercado"))
                    .assertNext(categoria -> assertThat(categoria.name()).isEqualTo("Salario"))
                    .verifyComplete();
        }

        private Set<CategoryScope> alcancesConsultadosPara(CategoryScope filtro) {
            when(query.findActiveByUser(eq(CategoryMother.USER_ID), any())).thenReturn(Flux.empty());

            StepVerifier.create(useCase().list(CategoryMother.USER_ID, filtro)).verifyComplete();

            verify(query).findActiveByUser(eq(CategoryMother.USER_ID), alcancesCapturados.capture());
            return alcancesCapturados.getValue();
        }
    }

    @Nested
    class Crear {

        @Test
        void guardaLaCategoriaDelUsuarioConLosCamposNormalizados() {
            altaPosible();

            StepVerifier.create(useCase().create(CategoryMother.USER_ID,
                            new CreateCategoryCommand("  Plantas  ", "expense", " sprout ", " #7cb342 ")))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(categorias).create(categoriaCreada.capture());
            NewCategory guardada = categoriaCreada.getValue();
            assertThat(guardada.userId()).isEqualTo(CategoryMother.USER_ID);
            assertThat(guardada.name()).isEqualTo("Plantas");
            assertThat(guardada.appliesTo()).isEqualTo(CategoryScope.EXPENSE);
            assertThat(guardada.icon()).isEqualTo("sprout");
            assertThat(guardada.color()).isEqualTo("#7CB342");
        }

        @Test
        void generaElIdentificadorComoUuidVersionSieteDelInstanteDelReloj() {
            altaPosible();

            StepVerifier.create(useCase().create(CategoryMother.USER_ID, CategoryMother.altaPlantas()))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(categorias).create(categoriaCreada.capture());
            assertThat(categoriaCreada.getValue().id().version()).isEqualTo(7);
            long milisDelId = categoriaCreada.getValue().id().getMostSignificantBits() >>> 16;
            assertThat(milisDelId).isEqualTo(RELOJ.millis());
        }

        @Test
        void devuelveLaCategoriaComoLaDejoLaBase() {
            altaPosible();

            StepVerifier.create(useCase().create(CategoryMother.USER_ID, CategoryMother.altaPlantas()))
                    .assertNext(categoria -> assertThat(categoria).isEqualTo(CategoryMother.plantasCreada()))
                    .verifyComplete();
        }

        @Test
        void propagaElConflictoQueReportaElRepositorio() {
            when(categorias.create(any())).thenReturn(Mono.error(new BadRequestException(HttpStatus.CONFLICT,
                    ErrorCodes.DUPLICATE_RESOURCE, "Ya hay una categoria con ese nombre", "name")));

            StepVerifier.create(useCase().create(CategoryMother.USER_ID, CategoryMother.altaPlantas()))
                    .expectErrorSatisfies(error -> assertThat(((BadRequestException) error).getHttpStatus())
                            .isEqualTo(HttpStatus.CONFLICT))
                    .verify();
        }

        @Test
        void noInsertaNadaHastaQueAlguienSeSuscribe() {
            useCase().create(CategoryMother.USER_ID, CategoryMother.altaPlantas());

            verifyNoInteractions(categorias);
        }

        private void altaPosible() {
            when(categorias.create(any())).thenReturn(Mono.just(CategoryMother.plantasCreada()));
        }
    }

    @Nested
    class Actualizar {

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
    }

    @Nested
    class Borrar {

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
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"))
                    .verify();
        }

        @Test
        void rechazaUnaCategoriaConSeriesActivasSinBorrarla() {
            conSeries(1L);

            StepVerifier.create(useCase().delete(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID))
                    .expectErrorSatisfies(error -> {
                        esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE, "id");
                        assertThat(((BadRequestException) error).getErrorResponse().getErrors().getFirst()
                                .getDescription()).contains("1 serie activa");
                    })
                    .verify();

            verify(categorias, never()).softDelete(any(), any());
        }

        private void conSeries(long cuantas) {
            when(series.countActiveByCategory(CategoryMother.PLANTAS_ID, CategoryMother.USER_ID))
                    .thenReturn(Mono.just(cuantas));
        }
    }

    private CategoryUseCase useCase() {
        return new CategoryUseCase(query, categorias, series, RELOJ);
    }

    private static void esError(Throwable error, HttpStatus status, ErrorCodes codigo, String campo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors())
                .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                .containsExactly(tuple(codigo.getCode(), campo));
    }
}
