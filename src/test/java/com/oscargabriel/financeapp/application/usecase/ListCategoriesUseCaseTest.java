package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.support.CategoryMother;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ListCategoriesUseCaseTest {

    @Mock
    private CategoryQueryPort query;

    @Captor
    private ArgumentCaptor<Set<CategoryScope>> alcancesCapturados;

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

    private ListCategoriesUseCase useCase() {
        return new ListCategoriesUseCase(query);
    }
}
