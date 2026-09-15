package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.RegisteredUser;
import com.oscargabriel.financeapp.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.domain.model.User;
import com.oscargabriel.financeapp.domain.port.in.RegisterUserPort;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.UserMother;

import reactor.core.publisher.Mono;

/**
 * El slice monta el controller sin el base-path /api: la ruta completa la verifican los requests de
 * bruno/auth/ contra un servidor real.
 */
@WebFluxTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String URI_REGISTRO = "/auth/register";

    private static final UUID ID = UUID.fromString("01994f00-0000-7000-8000-000000000001");

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RegisterUserPort registerUser;

    @Test
    void devuelve401CuandoNoHayCredenciales() {
        webTestClient.post().uri(URI_REGISTRO)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unPayload())
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(registerUser);
    }

    @Test
    void devuelve201ConElUsuarioCreado() {
        when(registerUser.register(any())).thenReturn(Mono.just(unRegistro()));

        webTestClient.mutateWith(mockUser())
                .post().uri(URI_REGISTRO)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unPayload())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(ID.toString())
                .jsonPath("$.email").isEqualTo(UserMother.EMAIL)
                .jsonPath("$.firstName").isEqualTo("Ana")
                .jsonPath("$.lastName").isEqualTo("Gomez")
                .jsonPath("$.baseCurrencyCode").isEqualTo("USD")
                .jsonPath("$.timezone").isEqualTo("America/Lima")
                .jsonPath("$.defaultCategories").isEqualTo(22);
    }

    /** OWASP A02: ni el hash ni la clave pueden asomar en la respuesta del alta. */
    @Test
    void nuncaDevuelveLaContrasenaNiSuHash() {
        when(registerUser.register(any())).thenReturn(Mono.just(unRegistro()));

        byte[] cuerpo = webTestClient.mutateWith(mockUser())
                .post().uri(URI_REGISTRO)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unPayload())
                .exchange()
                .expectStatus().isCreated()
                .expectBody().returnResult().getResponseBody();

        assertThat(new String(cuerpo))
                .doesNotContain(UserMother.PASSWORD)
                .doesNotContain(UserMother.HASH)
                .doesNotContain("passwordHash");
    }

    @Test
    void trasladaElPayloadTalCualAlCasoDeUso() {
        when(registerUser.register(any())).thenReturn(Mono.just(unRegistro()));

        webTestClient.mutateWith(mockUser())
                .post().uri(URI_REGISTRO)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unPayload())
                .exchange()
                .expectStatus().isCreated();

        verify(registerUser).register(new RegistrationCommand(
                UserMother.EMAIL, UserMother.PASSWORD, "Ana", "Gomez", "USD", "America/Lima"));
    }

    @Test
    void devuelve409ConElFormatoDeErrorDelProyectoCuandoElEmailYaExiste() {
        when(registerUser.register(any())).thenReturn(Mono.error(new BadRequestException(
                HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "Ya hay una cuenta registrada con ese correo", "email")));

        webTestClient.mutateWith(mockUser())
                .post().uri(URI_REGISTRO)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unPayload())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("DUPLICATE_RESOURCE")
                .jsonPath("$.errors[0].field").isEqualTo("email");
    }

    @Test
    void devuelve400CuandoElCuerpoNoEsJsonValido() {
        webTestClient.mutateWith(mockUser())
                .post().uri(URI_REGISTRO)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{ esto no es json")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(registerUser);
    }

    private static Map<String, Object> unPayload() {
        return Map.of(
                "email", UserMother.EMAIL,
                "password", UserMother.PASSWORD,
                "firstName", "Ana",
                "lastName", "Gomez",
                "baseCurrencyCode", "USD",
                "timezone", "America/Lima");
    }

    private static RegisteredUser unRegistro() {
        return new RegisteredUser(
                new User(ID, UserMother.EMAIL, UserMother.HASH, "Ana", "Gomez", "USD", "America/Lima"),
                22L);
    }
}
