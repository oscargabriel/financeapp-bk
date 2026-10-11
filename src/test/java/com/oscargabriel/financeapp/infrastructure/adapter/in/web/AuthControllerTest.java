package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
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
import com.oscargabriel.financeapp.domain.model.AccessToken;
import com.oscargabriel.financeapp.domain.model.LoginCommand;
import com.oscargabriel.financeapp.domain.model.RegisteredUser;
import com.oscargabriel.financeapp.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.domain.model.User;
import com.oscargabriel.financeapp.domain.port.in.LoginPort;
import com.oscargabriel.financeapp.domain.port.in.RegisterUserPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.BasicMother;
import com.oscargabriel.financeapp.support.TokenMother;
import com.oscargabriel.financeapp.support.UserMother;

import reactor.core.publisher.Mono;

/**
 * El slice monta el controller sin el base-path /api: la ruta completa la verifican los requests de
 * bruno/auth/ contra un servidor real.
 */
@WebFluxTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class AuthControllerTest {

    private static final String URI_REGISTRO = "/auth/register";

    private static final String URI_LOGIN = "/auth/login";

    private static final UUID ID = UUID.fromString("01994f00-0000-7000-8000-000000000001");

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RegisterUserPort registerUser;

    @MockitoBean
    private LoginPort login;

    /**
     * FA-43 cerro el alta detras del Basic: ya no hay ninguna ruta publica, asi que un cliente sin
     * la credencial compartida no puede ni crear una cuenta. Precio aceptado a cambio de que el
     * registro no quede expuesto al escaneo automatizado.
     */
    @Test
    void elRegistroExigeLaCredencialCompartida() {
        webTestClient.post().uri(URI_REGISTRO)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unPayload())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED");

        verifyNoInteractions(registerUser);
    }

    @Test
    void devuelve201ConElUsuarioCreado() {
        when(registerUser.register(any())).thenReturn(Mono.just(unRegistro()));

        webTestClient.post().uri(URI_REGISTRO)
                .headers(BasicMother.cabecera())
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
                .jsonPath("$.phone").isEqualTo(null)
                .jsonPath("$.defaultCategories").isEqualTo(22);
    }

    @Test
    void devuelveElCelularDelAltaYLoTrasladaAlCasoDeUso() {
        when(registerUser.register(any())).thenReturn(Mono.just(new RegisteredUser(
                new User(ID, UserMother.EMAIL, UserMother.HASH, "Ana", "Gomez", "USD", "America/Lima",
                        "+573001234567"),
                22L)));

        webTestClient.post().uri(URI_REGISTRO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "email", UserMother.EMAIL,
                        "password", UserMother.PASSWORD,
                        "firstName", "Ana",
                        "lastName", "Gomez",
                        "baseCurrencyCode", "USD",
                        "timezone", "America/Lima",
                        "phone", " +573001234567 "))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.phone").isEqualTo("+573001234567");

        verify(registerUser).register(UserMother.unAltaConCelular(" +573001234567 "));
    }

    @Test
    void rechazaUnCelularMalFormadoSinLlamarAlCasoDeUso() {
        webTestClient.post().uri(URI_REGISTRO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "email", UserMother.EMAIL,
                        "password", UserMother.PASSWORD,
                        "firstName", "Ana",
                        "phone", "300 123"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("phone");

        verifyNoInteractions(registerUser);
    }

    /** OWASP A02: ni el hash ni la clave pueden asomar en la respuesta del alta. */
    @Test
    void nuncaDevuelveLaContrasenaNiSuHash() {
        when(registerUser.register(any())).thenReturn(Mono.just(unRegistro()));

        byte[] cuerpo = webTestClient.post().uri(URI_REGISTRO)
                .headers(BasicMother.cabecera())
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

        webTestClient.post().uri(URI_REGISTRO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unPayload())
                .exchange()
                .expectStatus().isCreated();

        verify(registerUser).register(new RegistrationCommand(
                UserMother.EMAIL, UserMother.PASSWORD, "Ana", "Gomez", "USD", "America/Lima", null));
    }

    @Test
    void devuelve409ConElFormatoDeErrorDelProyectoCuandoElEmailYaExiste() {
        when(registerUser.register(any())).thenReturn(Mono.error(new BadRequestException(
                HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "Ya hay una cuenta registrada con ese correo", "email")));

        webTestClient.post().uri(URI_REGISTRO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unPayload())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("DUPLICATE_RESOURCE")
                .jsonPath("$.errors[0].field").isEqualTo("email");
    }

    /** FA-103: el handler responde con el status que trae la excepcion, sin un case propio. */
    @Test
    void devuelve403ConElFormatoDeErrorDelProyectoCuandoElCorreoNoEstaAdmitido() {
        when(registerUser.register(any())).thenReturn(Mono.error(new BadRequestException(
                HttpStatus.FORBIDDEN, ErrorCodes.REGISTRATION_NOT_ALLOWED,
                "El correo no esta admitido para registrarse", "email")));

        webTestClient.post().uri(URI_REGISTRO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unPayload())
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo("REGISTRATION_NOT_ALLOWED")
                .jsonPath("$.errors[0].field").isEqualTo("email");
    }

    /**
     * Las reglas viven en el record: un payload invalido no llega al caso de uso, y por eso tampoco
     * a la lista de admitidos (FA-103): el 400 sale antes que el 403.
     */
    @Test
    void devuelve400ConTodosLosCamposInvalidosSinLlamarAlCasoDeUso() {
        webTestClient.post().uri(URI_REGISTRO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", "no-es-un-email", "password", "corta", "firstName", " "))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(3)
                .jsonPath("$.errors[*].code").value(codigos -> assertThat(codigos)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                        .containsOnly("VALIDATION_ERROR"))
                .jsonPath("$.errors[?(@.field == 'email')].description")
                .isEqualTo("El correo no tiene un formato valido")
                .jsonPath("$.errors[?(@.field == 'password')].description")
                .isEqualTo("La contrasena debe tener al menos 8 caracteres")
                .jsonPath("$.errors[?(@.field == 'firstName')].description")
                .isEqualTo("El nombre es obligatorio");

        verifyNoInteractions(registerUser);
    }

    @Test
    void elLoginDevuelve400SinLlamarAlCasoDeUsoCuandoFaltanLasCredenciales() {
        webTestClient.post().uri(URI_LOGIN)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", " "))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(2)
                .jsonPath("$.errors[?(@.field == 'email')].description").isEqualTo("El correo es obligatorio")
                .jsonPath("$.errors[?(@.field == 'password')].description")
                .isEqualTo("La contrasena es obligatoria");

        verifyNoInteractions(login);
    }

    @Test
    void devuelve400CuandoElCuerpoNoEsJsonValido() {
        webTestClient.post().uri(URI_REGISTRO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{ esto no es json")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(registerUser);
    }

    @Test
    void devuelve200ConElTokenYSuVigenciaEnSegundos() {
        when(login.login(any()))
                .thenReturn(Mono.just(new AccessToken("un.jwt.firmado", Duration.ofHours(1))));

        webTestClient.post().uri(URI_LOGIN)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unasCredenciales())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("un.jwt.firmado")
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isEqualTo(3600);
    }

    @Test
    void trasladaLasCredencialesTalCualAlCasoDeUso() {
        when(login.login(any()))
                .thenReturn(Mono.just(new AccessToken("un.jwt.firmado", Duration.ofHours(1))));

        webTestClient.post().uri(URI_LOGIN)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unasCredenciales())
                .exchange()
                .expectStatus().isOk();

        verify(login).login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD));
    }

    @Test
    void devuelve401ConElFormatoDeErrorDelProyectoCuandoLasCredencialesNoSirven() {
        when(login.login(any())).thenReturn(Mono.error(new BadRequestException(
                HttpStatus.UNAUTHORIZED, ErrorCodes.INVALID_CREDENTIALS,
                "Correo o contrasena incorrectos", "credentials")));

        webTestClient.post().uri(URI_LOGIN)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unasCredenciales())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("INVALID_CREDENTIALS")
                .jsonPath("$.errors[0].description").isEqualTo("Correo o contrasena incorrectos");
    }

    /** El token es una credencial: no puede acabar en un log ni en el eco de la peticion. */
    @Test
    void laRespuestaDelLoginNoDevuelveLaClaveQueSeMando() {
        when(login.login(any()))
                .thenReturn(Mono.just(new AccessToken("un.jwt.firmado", Duration.ofHours(1))));

        byte[] cuerpo = webTestClient.post().uri(URI_LOGIN)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unasCredenciales())
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();

        assertThat(new String(cuerpo)).doesNotContain(UserMother.PASSWORD);
    }

    @Test
    void elLoginTambienExigeLaCredencialCompartida() {
        webTestClient.post().uri(URI_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unasCredenciales())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED");

        verifyNoInteractions(login);
    }

    /** El Basic abre estas dos rutas y nada mas: el JWT no entra por aqui. */
    @Test
    void unTokenValidoNoSirveEnLasRutasDeAuth() {
        webTestClient.post().uri(URI_LOGIN)
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unasCredenciales())
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(login);
    }

    @Test
    void unTokenValidoNoSirveEnElRegistro() {
        webTestClient.post().uri(URI_REGISTRO)
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unPayload())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED");

        verifyNoInteractions(registerUser);
    }

    /**
     * Con Basic son esas dos rutas y nada mas: cualquier otra bajo /auth cae en la cadena del JWT,
     * donde la credencial compartida no vale.
     */
    @Test
    void ningunaOtraRutaDeAuthAceptaElBasic() {
        webTestClient.post().uri("/auth/refresh")
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unasCredenciales())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED");
    }

    /** El Basic abre el alta para POST, no para cualquier verbo. */
    @Test
    void elRegistroSoloAceptaElBasicEnPost() {
        webTestClient.get().uri(URI_REGISTRO)
                .headers(BasicMother.cabecera())
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(registerUser);
    }

    private static Map<String, String> unasCredenciales() {
        return Map.of("email", UserMother.EMAIL, "password", UserMother.PASSWORD);
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
                new User(ID, UserMother.EMAIL, UserMother.HASH, "Ana", "Gomez", "USD", "America/Lima", null),
                22L);
    }
}
