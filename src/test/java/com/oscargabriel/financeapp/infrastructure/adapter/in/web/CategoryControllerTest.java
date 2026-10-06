package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateCategoryCommand;
import com.oscargabriel.financeapp.domain.model.UpdateCategoryCommand;
import com.oscargabriel.financeapp.domain.port.in.CreateCategoryPort;
import com.oscargabriel.financeapp.domain.port.in.UpdateCategoryPort;
import com.oscargabriel.financeapp.domain.port.in.ListCategoriesPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.CategoryMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre CategoriesIT. */
@WebFluxTest(CategoryController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class CategoryControllerTest {

    private static final String URI_BASE = "/categories";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ListCategoriesPort listCategories;

    @MockitoBean
    private CreateCategoryPort createCategory;

    @MockitoBean
    private UpdateCategoryPort updateCategory;

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

    @Test
    void creaLaCategoriaYRespondeConElMismoContratoQueElListado() {
        when(createCategory.create(eq(CategoryMother.USER_ID), any()))
                .thenReturn(Mono.just(CategoryMother.plantasCreada()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Plantas", "appliesTo": "EXPENSE", "icon": "sprout", "color": "#7CB342"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("30000000-0000-7000-8000-000000000004")
                .jsonPath("$.name").isEqualTo("Plantas")
                .jsonPath("$.appliesTo").isEqualTo("EXPENSE")
                .jsonPath("$.icon").isEqualTo("sprout")
                .jsonPath("$.color").isEqualTo("#7CB342")
                .jsonPath("$.isSystem").isEqualTo(false)
                .jsonPath("$.userId").doesNotExist();
    }

    @Test
    void pasaAlCasoDeUsoElUsuarioDelTokenYElCuerpoTalCualLlega() {
        when(createCategory.create(eq(CategoryMother.USER_ID), any()))
                .thenReturn(Mono.just(CategoryMother.plantasCreada()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "  Plantas  ", "appliesTo": "expense", "icon": " ", "color": "#7cb342"}
                        """)
                .exchange()
                .expectStatus().isCreated();

        ArgumentCaptor<CreateCategoryCommand> comando = ArgumentCaptor.forClass(CreateCategoryCommand.class);
        verify(createCategory).create(eq(CategoryMother.USER_ID), comando.capture());
        assertThat(comando.getValue()).isEqualTo(new CreateCategoryCommand("  Plantas  ", "expense", " ", "#7cb342"));
    }

    /** Las reglas de formato viven en el record: un cuerpo invalido no llega al caso de uso. */
    @Test
    void devuelve400ConLosCamposInvalidosSinLlamarAlCasoDeUso() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"appliesTo": "GASTO", "color": "rojo"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(3)
                .jsonPath("$.errors[?(@.field == 'name')].description").isEqualTo("El nombre es obligatorio")
                .jsonPath("$.errors[?(@.field == 'appliesTo')].description")
                .isEqualTo("appliesTo debe ser EXPENSE, INCOME o BOTH")
                .jsonPath("$.errors[?(@.field == 'color')].description")
                .isEqualTo("El color debe tener la forma #RRGGBB")
                .jsonPath("$.errors[?(@.code != 'VALIDATION_ERROR')]").isEmpty();

        verifyNoInteractions(createCategory);
    }

    @Test
    void devuelve409CuandoElNombreYaLoUsaOtraCategoriaViva() {
        when(createCategory.create(eq(CategoryMother.USER_ID), any()))
                .thenReturn(Mono.error(new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                        "Ya hay una categoria con ese nombre", "name")));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Mercado", "appliesTo": "EXPENSE"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.DUPLICATE_RESOURCE.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("name");
    }

    @Test
    void devuelve400CuandoElCuerpoNoEsJson() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{name:")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.JSON_PARSING_ERROR.getCode());

        verifyNoInteractions(createCategory);
    }

    @Test
    void devuelve401AlCrearSinCredenciales() {
        webTestClient.post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Plantas", "appliesTo": "EXPENSE"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(createCategory);
    }

    private static final String URI_PLANTAS = URI_BASE + "/" + CategoryMother.PLANTAS_ID;

    @Test
    void modificaLaCategoriaYRespondeConElMismoContratoQueElListado() {
        when(updateCategory.update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), any()))
                .thenReturn(Mono.just(CategoryMother.plantasCreada()));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PLANTAS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"color": "#7CB342"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(CategoryMother.PLANTAS_ID.toString())
                .jsonPath("$.name").isEqualTo("Plantas")
                .jsonPath("$.appliesTo").isEqualTo("EXPENSE")
                .jsonPath("$.color").isEqualTo("#7CB342")
                .jsonPath("$.isSystem").isEqualTo(false)
                .jsonPath("$.userId").doesNotExist();
    }

    @Test
    void pasaAlCasoDeUsoElUsuarioElIdYElParcheTalCualLlega() {
        when(updateCategory.update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), any()))
                .thenReturn(Mono.just(CategoryMother.plantasCreada()));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PLANTAS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": " Huerta ", "appliesTo": "both", "icon": null}
                        """)
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<UpdateCategoryCommand> comando = ArgumentCaptor.forClass(UpdateCategoryCommand.class);
        verify(updateCategory).update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), comando.capture());
        assertThat(comando.getValue()).isEqualTo(new UpdateCategoryCommand(" Huerta ", "both", null, null));
    }

    @Test
    void devuelve400SobreElBodyCuandoElParcheNoTraeCambios() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PLANTAS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": null}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("body");

        verifyNoInteractions(updateCategory);
    }

    @Test
    void devuelve400ConLosCamposDelParcheInvalidosSinLlamarAlCasoDeUso() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PLANTAS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": " ", "appliesTo": "GASTO", "icon": "", "color": "rojo"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(4)
                .jsonPath("$.errors[?(@.code != 'VALIDATION_ERROR')]").isEmpty();

        verifyNoInteractions(updateCategory);
    }

    @Test
    void devuelve400SobreElIdCuandoNoEsUnUuid() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/abc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Huerta"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verifyNoInteractions(updateCategory);
    }

    @Test
    void devuelve404CuandoLaCategoriaNoEsDelUsuario() {
        when(updateCategory.update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), any()))
                .thenReturn(Mono.error(new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                        ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "La categoria no existe", "id")))));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PLANTAS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Huerta"}
                        """)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.NOT_FOUND.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("id");
    }

    @Test
    void devuelve409CuandoElNombreNuevoYaLoUsaOtraCategoria() {
        when(updateCategory.update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), any()))
                .thenReturn(Mono.error(new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                        "Ya hay una categoria con ese nombre", "name")));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PLANTAS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Bonos"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.DUPLICATE_RESOURCE.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("name");
    }

    @Test
    void devuelve409CuandoElAlcanceNoAdmiteLosMovimientosDeLaCategoria() {
        when(updateCategory.update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), any()))
                .thenReturn(Mono.error(new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                        "La categoria tiene movimientos de un tipo que el nuevo alcance no admite", "appliesTo")));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PLANTAS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"appliesTo": "INCOME"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.RESOURCE_IN_USE.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("appliesTo");
    }

    @Test
    void devuelve401AlModificarSinCredenciales() {
        webTestClient.patch().uri(URI_PLANTAS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Huerta"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(updateCategory);
    }
}
