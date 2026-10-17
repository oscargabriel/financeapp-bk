package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

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

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.CategoryScope;
import com.oscargabriel.financeapp.core.domain.model.CreateCategoryCommand;
import com.oscargabriel.financeapp.core.domain.model.UpdateCategoryCommand;
import com.oscargabriel.financeapp.core.domain.port.in.CategoryPort;
import com.oscargabriel.financeapp.compartido.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.compartido.support.SesionSiempreAbierta;
import com.oscargabriel.financeapp.compartido.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.core.support.CategoryMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre CategoriesIT. */
@WebFluxTest(CategoryController.class)
@Import({SecurityConfig.class, JwtConfig.class, SesionSiempreAbierta.class})
class CategoryControllerTest {

    private static final String URI_BASE = "/categories";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CategoryPort categorias;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(CategoryMother.USER_ID.toString()));
    }

    @Test
    void devuelve401CuandoNoHayCredenciales() {
        webTestClient.get().uri(URI_BASE)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(categorias);
    }

    @Test
    void devuelveLasCategoriasConElFormatoDelContrato() {
        when(categorias.list(eq(CategoryMother.USER_ID), any()))
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
    void devuelveUnaCategoriaPropiaConSuAlcanceIconoYColor() {
        when(categorias.list(eq(CategoryMother.USER_ID), any()))
                .thenReturn(Flux.just(CategoryMother.ajustes()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].icon").isEqualTo("sliders-horizontal")
                .jsonPath("$[0].color").isEqualTo("#607D8B")
                .jsonPath("$[0].appliesTo").isEqualTo("BOTH")
                .jsonPath("$[0].isSystem").isEqualTo(false);
    }

    @Test
    void devuelveUnArrayVacioCuandoElUsuarioNoTieneCategorias() {
        when(categorias.list(eq(CategoryMother.USER_ID), any())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    @Test
    void pasaAlCasoDeUsoElFiltroParseado() {
        when(categorias.list(eq(CategoryMother.USER_ID), any())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?appliesTo=INCOME")
                .exchange()
                .expectStatus().isOk();

        verify(categorias).list(CategoryMother.USER_ID, CategoryScope.INCOME);
    }

    @Test
    void pasaNullAlCasoDeUsoCuandoNoLlegaElFiltro() {
        when(categorias.list(eq(CategoryMother.USER_ID), any())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk();

        verify(categorias).list(CategoryMother.USER_ID, null);
    }

    @Test
    void devuelve400CuandoElFiltroNoEsUnAlcanceConocido() {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?appliesTo=GASTO")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("appliesTo");

        verifyNoInteractions(categorias);
    }

    /** El contrato publica los valores en mayusculas, igual que la columna. */
    @Test
    void devuelve400CuandoElFiltroLlegaEnMinusculas() {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?appliesTo=expense")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("appliesTo");

        verifyNoInteractions(categorias);
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

        verifyNoInteractions(categorias);
    }

    @Test
    void creaLaCategoriaYRespondeConElMismoContratoQueElListado() {
        when(categorias.create(eq(CategoryMother.USER_ID), any()))
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
        when(categorias.create(eq(CategoryMother.USER_ID), any()))
                .thenReturn(Mono.just(CategoryMother.plantasCreada()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "  Plantas  ", "appliesTo": "expense", "icon": " sprout ", "color": "#7cb342"}
                        """)
                .exchange()
                .expectStatus().isCreated();

        ArgumentCaptor<CreateCategoryCommand> comando = ArgumentCaptor.forClass(CreateCategoryCommand.class);
        verify(categorias).create(eq(CategoryMother.USER_ID), comando.capture());
        assertThat(comando.getValue()).isEqualTo(new CreateCategoryCommand("  Plantas  ", "expense", " sprout ", "#7cb342"));
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
                .jsonPath("$.errors.length()").isEqualTo(4)
                .jsonPath("$.errors[?(@.field == 'name')].description").isEqualTo("El nombre es obligatorio")
                .jsonPath("$.errors[?(@.field == 'icon')].description").isEqualTo("El icono es obligatorio")
                .jsonPath("$.errors[?(@.field == 'appliesTo')].description")
                .isEqualTo("appliesTo debe ser EXPENSE, INCOME o BOTH")
                .jsonPath("$.errors[?(@.field == 'color')].description")
                .isEqualTo("El color debe tener la forma #RRGGBB")
                .jsonPath("$.errors[?(@.code != 'VALIDATION_ERROR')]").isEmpty();

        verifyNoInteractions(categorias);
    }

    @Test
    void devuelve409CuandoElNombreYaLoUsaOtraCategoriaViva() {
        when(categorias.create(eq(CategoryMother.USER_ID), any()))
                .thenReturn(Mono.error(new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                        "Ya hay una categoria con ese nombre", "name")));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Mercado", "appliesTo": "EXPENSE", "icon": "tag", "color": "#455A64"}
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

        verifyNoInteractions(categorias);
    }

    @Test
    void devuelve401AlCrearSinCredenciales() {
        webTestClient.post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Plantas", "appliesTo": "EXPENSE", "icon": "sprout", "color": "#7CB342"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(categorias);
    }

    private static final String URI_PLANTAS = URI_BASE + "/" + CategoryMother.PLANTAS_ID;

    @Test
    void modificaLaCategoriaYRespondeConElMismoContratoQueElListado() {
        when(categorias.update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), any()))
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
        when(categorias.update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), any()))
                .thenReturn(Mono.just(CategoryMother.plantasCreada()));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PLANTAS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": " Huerta ", "appliesTo": "both", "icon": null}
                        """)
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<UpdateCategoryCommand> comando = ArgumentCaptor.forClass(UpdateCategoryCommand.class);
        verify(categorias).update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), comando.capture());
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

        verifyNoInteractions(categorias);
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

        verifyNoInteractions(categorias);
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

        verifyNoInteractions(categorias);
    }

    @Test
    void devuelve404CuandoLaCategoriaNoEsDelUsuario() {
        when(categorias.update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), any()))
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
        when(categorias.update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), any()))
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
        when(categorias.update(eq(CategoryMother.USER_ID), eq(CategoryMother.PLANTAS_ID), any()))
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

        verifyNoInteractions(categorias);
    }

    @Test
    void borraLaCategoriaDelUsuarioDelTokenYRespondeSinCuerpo() {
        when(categorias.delete(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID)).thenReturn(Mono.empty());

        webTestClient.mutateWith(tokenDelUsuario()).delete().uri(URI_PLANTAS)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(categorias).delete(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID);
    }

    @Test
    void devuelve400SobreElIdAlBorrarCuandoNoEsUnUuid() {
        webTestClient.mutateWith(tokenDelUsuario()).delete().uri(URI_BASE + "/abc")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verifyNoInteractions(categorias);
    }

    @Test
    void devuelve404AlBorrarUnaCategoriaQueNoEsDelUsuario() {
        when(categorias.delete(CategoryMother.USER_ID, CategoryMother.PLANTAS_ID))
                .thenReturn(Mono.error(new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                        ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "La categoria no existe", "id")))));

        webTestClient.mutateWith(tokenDelUsuario()).delete().uri(URI_PLANTAS)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.NOT_FOUND.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("id");
    }

    @Test
    void devuelve401AlBorrarSinCredenciales() {
        webTestClient.delete().uri(URI_PLANTAS)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(categorias);
    }
}
