package com.oscargabriel.financeapp.core.application.usecase;

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

import com.oscargabriel.financeapp.core.domain.model.CategoryScope;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.support.CategoryMother;
import com.oscargabriel.financeapp.core.support.CurrencyMother;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CatalogUseCaseTest {

    @Mock
    private CurrencyQueryPort monedas;

    @Mock
    private CategoryQueryPort categorias;

    @Captor
    private ArgumentCaptor<Set<CategoryScope>> alcancesCapturados;

    @Test
    void emiteLasMonedasActivasEnElOrdenQueEntregaElPuerto() {
        when(monedas.findActive()).thenReturn(Flux.just(CurrencyMother.cop(), CurrencyMother.usd()));

        StepVerifier.create(useCase().listCurrencies())
                .expectNext(CurrencyMother.cop(), CurrencyMother.usd())
                .verifyComplete();
    }

    @Test
    void sinFiltroConsultaLasCategoriasDeLosTresAlcances() {
        assertThat(alcancesConsultadosPara(null))
                .containsExactlyInAnyOrder(CategoryScope.EXPENSE, CategoryScope.INCOME, CategoryScope.BOTH);
    }

    @Test
    void conFiltroDeGastoConsultaLasCategoriasDeGastoYAmbos() {
        assertThat(alcancesConsultadosPara(CategoryScope.EXPENSE))
                .containsExactlyInAnyOrder(CategoryScope.EXPENSE, CategoryScope.BOTH);
    }

    private Set<CategoryScope> alcancesConsultadosPara(CategoryScope filtro) {
        when(categorias.findActiveByUser(eq(CategoryMother.USER_ID), any())).thenReturn(Flux.empty());

        StepVerifier.create(useCase().listCategories(CategoryMother.USER_ID, filtro)).verifyComplete();

        verify(categorias).findActiveByUser(eq(CategoryMother.USER_ID), alcancesCapturados.capture());
        return alcancesCapturados.getValue();
    }

    private CatalogUseCase useCase() {
        return new CatalogUseCase(monedas, categorias);
    }
}
