package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateCategoryCommand;
import com.oscargabriel.financeapp.domain.model.NewCategory;
import com.oscargabriel.financeapp.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.support.CategoryMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CreateCategoryUseCaseTest {

    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-10-05T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private CategoryRepositoryPort categorias;

    @Captor
    private ArgumentCaptor<NewCategory> categoriaGuardada;

    @Test
    void guardaLaCategoriaDelUsuarioConLosCamposNormalizados() {
        altaPosible();

        StepVerifier.create(useCase().create(CategoryMother.USER_ID,
                        new CreateCategoryCommand("  Plantas  ", "expense", " sprout ", " #7cb342 ")))
                .expectNextCount(1)
                .verifyComplete();

        verify(categorias).create(categoriaGuardada.capture());
        NewCategory guardada = categoriaGuardada.getValue();
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

        verify(categorias).create(categoriaGuardada.capture());
        assertThat(categoriaGuardada.getValue().id().version()).isEqualTo(7);
        long milisDelId = categoriaGuardada.getValue().id().getMostSignificantBits() >>> 16;
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

    private CreateCategoryUseCase useCase() {
        return new CreateCategoryUseCase(categorias, RELOJ);
    }
}
