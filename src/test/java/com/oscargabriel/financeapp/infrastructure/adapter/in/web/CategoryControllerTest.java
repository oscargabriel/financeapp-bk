package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.port.in.ListCategoriesPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.CategoryMother;

import reactor.core.publisher.Flux;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre CategoriesIT. */
@WebFluxTest(CategoryController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class CategoryControllerTest {

    private static final String URI_BASE = "/categories";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ListCategoriesPort listCategories;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(CategoryMother.USER_ID.toString()));
    }

    @Test
    void devuelve401CuandoNoHayCredenciales() {
        webTestClient.get().uri(URI_BASE)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(listCategories);
    }

    @Test
    void devuelveLasCategoriasConElFormatoDelContrato() {
        when(listCategories.list(eq(CategoryMother.USER_ID), any()))
                .thenReturn(Flux.just(CategoryMother.mercado()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].id").isEqualTo(CategoryMother.MERCADO_ID.toString())
                .jsonPath("$[0].name").isEqualTo("Mercado")
                .jsonPath("$[0].appliesTo").isEqualTo("EXPENSE")
                .jsonPath("$[0].icon").isEqualTo("shopping-cart")
                .jsonPath("$[0].color").isEqualTo("#2E7D32")
                .jsonPath("$[0].isSystem").isEqualTo(true)
                .jsonPath("$[0].userId").doesNotExist();
    }

    @Test
    void mantieneEnNullElIconoYElColorQueNoTieneLaCategoria() {
        when(listCategories.list(eq(CategoryMother.USER_ID), any()))
                .thenReturn(Flux.just(CategoryMother.propiaSinIconoNiColor()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].icon").isEqualTo(null)
                .jsonPath("$[0].color").isEqualTo(null)
                .jsonPath("$[0].appliesTo").isEqualTo("BOTH")
                .jsonPath("$[0].isSystem").isEqualTo(false);
    }

    @Test
    void devuelveUnArrayVacioCuandoElUsuarioNoTieneCategorias() {
        when(listCategories.list(eq(CategoryMother.USER_ID), any())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    @Test
    void pasaAlCasoDeUsoElFiltroParseado() {
        when(listCategories.list(eq(CategoryMother.USER_ID), any())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?appliesTo=INCOME")
                .exchange()
                .expectStatus().isOk();

        verify(listCategories).list(CategoryMother.USER_ID, CategoryScope.INCOME);
    }

    @Test
    void pasaNullAlCasoDeUsoCuandoNoLlegaElFiltro() {
        when(listCategories.list(eq(CategoryMother.USER_ID), any())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk();

        verify(listCategories).list(CategoryMother.USER_ID, null);
    }

    @Test
    void devuelve400CuandoElFiltroNoEsUnAlcanceConocido() {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?appliesTo=GASTO")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("appliesTo");

        verifyNoInteractions(listCategories);
    }

    /** El contrato publica los valores en mayusculas, igual que la columna. */
    @Test
    void devuelve400CuandoElFiltroLlegaEnMinusculas() {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?appliesTo=expense")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("appliesTo");

        verifyNoInteractions(listCategories);
    }

    @Test
    void devuelve401CuandoElSubjectDelTokenNoEsUnUuid() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("no-es-uuid")))
                .get().uri(URI_BASE)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.UNAUTHENTICATED.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("authorization");

        verifyNoInteractions(listCategories);
    }
}
