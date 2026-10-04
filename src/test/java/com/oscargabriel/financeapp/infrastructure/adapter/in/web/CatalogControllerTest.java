package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.port.in.ListCategoriesPort;
import com.oscargabriel.financeapp.domain.port.in.ListCurrenciesPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.CategoryMother;
import com.oscargabriel.financeapp.support.CurrencyMother;

import reactor.core.publisher.Flux;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre CatalogsIT. */
@WebFluxTest(CatalogController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class CatalogControllerTest {

    private static final String URI_BASE = "/catalogs";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ListCurrenciesPort listCurrencies;

    @MockitoBean
    private ListCategoriesPort listCategories;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(CategoryMother.USER_ID.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/account-types", "/transaction-types", "/currencies", "/categories"})
    void devuelve401CuandoNoHayCredenciales(String ruta) {
        webTestClient.get().uri(URI_BASE + ruta)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(listCurrencies, listCategories);
    }

    @Test
    void devuelveLosTiposDeCuentaEnElOrdenDelEnumConSuEtiqueta() {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/account-types")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        [{"code": "CASH", "description": "Efectivo"},
                         {"code": "DEBIT", "description": "Cuenta débito"},
                         {"code": "CREDIT", "description": "Tarjeta de crédito"},
                         {"code": "SAVINGS", "description": "Cuenta de ahorros"},
                         {"code": "INVESTMENT", "description": "Inversión"},
                         {"code": "OTHER", "description": "Otra"}]
                        """, true);
    }

    @Test
    void devuelveLosTiposDeMovimientoEnElOrdenDelEnumConSuEtiqueta() {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/transaction-types")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        [{"code": "EXPENSE", "description": "Gasto"},
                         {"code": "INCOME", "description": "Ingreso"},
                         {"code": "TRANSFER", "description": "Transferencia"}]
                        """, true);
    }

    @Test
    void devuelveLasMonedasActivasConCodigoNombreYSimbolo() {
        when(listCurrencies.listActive()).thenReturn(Flux.just(CurrencyMother.cop(), CurrencyMother.usd()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/currencies")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        [{"code": "COP", "name": "Peso colombiano", "symbol": "$"},
                         {"code": "USD", "name": "Dólar estadounidense", "symbol": "US$"}]
                        """, true);
    }

    @Test
    void devuelveLasCategoriasDelUsuarioDelTokenSoloConIdYNombre() {
        when(listCategories.list(eq(CategoryMother.USER_ID), any()))
                .thenReturn(Flux.just(CategoryMother.mercado(), CategoryMother.propiaSinIconoNiColor()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/categories")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        [{"id": "%s", "name": "Mercado"},
                         {"id": "30000000-0000-7000-8000-000000000003", "name": "Ajustes"}]
                        """.formatted(CategoryMother.MERCADO_ID), true);
    }

    @Test
    void pasaNullAlCasoDeUsoCuandoNoLlegaElFiltro() {
        when(listCategories.list(eq(CategoryMother.USER_ID), any())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/categories")
                .exchange()
                .expectStatus().isOk();

        verify(listCategories).list(CategoryMother.USER_ID, null);
    }

    @Test
    void pasaAlCasoDeUsoElFiltroParseado() {
        when(listCategories.list(eq(CategoryMother.USER_ID), any())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/categories?appliesTo=EXPENSE")
                .exchange()
                .expectStatus().isOk();

        verify(listCategories).list(CategoryMother.USER_ID, CategoryScope.EXPENSE);
    }

    /** El mismo contrato que GET /categories: exacto y en mayusculas. */
    @ParameterizedTest
    @ValueSource(strings = {"GASTO", "expense"})
    void devuelve400CuandoElFiltroNoEsUnAlcanceConocido(String filtro) {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/categories?appliesTo=" + filtro)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].description").isEqualTo("appliesTo debe ser EXPENSE, INCOME o BOTH")
                .jsonPath("$.errors[0].field").isEqualTo("appliesTo");

        verifyNoInteractions(listCategories);
    }

    @Test
    void devuelve401CuandoElSubjectDelTokenNoEsUnUuid() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("no-es-uuid")))
                .get().uri(URI_BASE + "/categories")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.UNAUTHENTICATED.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("authorization");

        verifyNoInteractions(listCategories);
    }
}
