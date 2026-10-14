package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.AccessToken;
import com.oscargabriel.financeapp.core.domain.model.GoogleRegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.LoginCommand;
import com.oscargabriel.financeapp.core.domain.model.RefreshToken;
import com.oscargabriel.financeapp.core.domain.model.RegisteredUser;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.User;
import com.oscargabriel.financeapp.core.domain.port.in.GoogleLoginPort;
import com.oscargabriel.financeapp.core.domain.port.in.GoogleRegisterPort;
import com.oscargabriel.financeapp.core.domain.port.in.LoginPort;
import com.oscargabriel.financeapp.core.domain.port.in.LogoutPort;
import com.oscargabriel.financeapp.core.domain.port.in.RefreshSessionPort;
import com.oscargabriel.financeapp.core.domain.port.in.RegisterUserPort;
import com.oscargabriel.financeapp.compartido.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.compartido.support.SesionSiempreAbierta;
import com.oscargabriel.financeapp.compartido.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.compartido.support.BasicMother;
import com.oscargabriel.financeapp.compartido.support.TokenMother;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Mono;

/**
 * El slice monta el controller sin el base-path /api: la ruta completa la verifican los requests de
 * bruno/auth/ contra un servidor real.
 */
@WebFluxTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class, SesionSiempreAbierta.class})
class AuthControllerTest {

    private static final String URI_REGISTRO = "/auth/register";

    private static final String URI_LOGIN = "/auth/login";

    private static final String URI_REFRESCO = "/auth/refresh";

    private static final String URI_LOGOUT = "/auth/logout";

    private static final String URI_GOOGLE_REGISTRO = "/auth/google/register";

    private static final String URI_GOOGLE_LOGIN = "/auth/google/login";

    private static final UUID ID = UUID.fromString("01994f00-0000-7000-8000-000000000001");

    private static final SessionTokens SESION = new SessionTokens(
            new AccessToken("un.jwt.firmado", Duration.ofHours(1)),
            new RefreshToken("un-refresh-opaco", Duration.ofHours(24)));

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RegisterUserPort registerUser;

    @MockitoBean
    private LoginPort login;

    @MockitoBean
    private RefreshSessionPort refreshSession;

    @MockitoBean
    private LogoutPort logout;

    @MockitoBean
    private GoogleRegisterPort googleRegister;

    @MockitoBean
    private GoogleLoginPort googleLogin;

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
                        "+573001234567", null),
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
    void devuelve200ConLosDosTokensYSusVigenciasEnSegundos() {
        when(login.login(any()))
                .thenReturn(Mono.just(SESION));

        webTestClient.post().uri(URI_LOGIN)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(unasCredenciales())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("un.jwt.firmado")
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isEqualTo(3600)
                .jsonPath("$.refreshToken").isEqualTo("un-refresh-opaco")
                .jsonPath("$.refreshExpiresIn").isEqualTo(86400);
    }

    @Test
    void trasladaLasCredencialesTalCualAlCasoDeUso() {
        when(login.login(any()))
                .thenReturn(Mono.just(SESION));

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
                .thenReturn(Mono.just(SESION));

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

    @Test
    void elRefrescoDevuelve200ConLaMismaFormaDelLogin() {
        when(refreshSession.refresh("un-refresh-vigente")).thenReturn(Mono.just(SESION));

        webTestClient.post().uri(URI_REFRESCO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", "un-refresh-vigente"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("un.jwt.firmado")
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isEqualTo(3600)
                .jsonPath("$.refreshToken").isEqualTo("un-refresh-opaco")
                .jsonPath("$.refreshExpiresIn").isEqualTo(86400);
    }

    @Test
    void elRefrescoDevuelve401ConElCampoDelRefreshCuandoNoSirve() {
        when(refreshSession.refresh(any())).thenReturn(Mono.error(new BadRequestException(
                HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, "Autenticacion requerida",
                "refreshToken")));

        webTestClient.post().uri(URI_REFRESCO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", "uno-usado"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("refreshToken");
    }

    @Test
    void elLogoutDevuelve204SinCuerpo() {
        when(logout.logout("un-refresh-vigente")).thenReturn(Mono.empty());

        webTestClient.post().uri(URI_LOGOUT)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", "un-refresh-vigente"))
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(logout).logout("un-refresh-vigente");
    }

    @ParameterizedTest
    @ValueSource(strings = {URI_REFRESCO, URI_LOGOUT})
    void sinRefreshTokenDevuelve400SinLlamarAlCasoDeUso(String ruta) {
        webTestClient.post().uri(ruta)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", "  "))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("refreshToken");

        verifyNoInteractions(refreshSession, logout);
    }

    @ParameterizedTest
    @ValueSource(strings = {URI_REFRESCO, URI_LOGOUT})
    void elRefrescoYElLogoutExigenLaCredencialCompartida(String ruta) {
        webTestClient.post().uri(ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", "un-refresh-vigente"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Basic")
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("authorization");

        verifyNoInteractions(refreshSession, logout);
    }

    /** El refresh no es un Bearer: con un access valido en vez del Basic, la cadena lo rechaza. */
    @ParameterizedTest
    @ValueSource(strings = {URI_REFRESCO, URI_LOGOUT})
    void unTokenValidoNoSirveEnElRefrescoNiEnElLogout(String ruta) {
        webTestClient.post().uri(ruta)
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", "un-refresh-vigente"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Basic");

        verifyNoInteractions(refreshSession, logout);
    }

    /**
     * Con Basic son las rutas de auth que existen y nada mas: cualquier otra bajo /auth cae en la
     * cadena del JWT, donde la credencial compartida no vale.
     */
    @Test
    void ningunaOtraRutaDeAuthAceptaElBasic() {
        webTestClient.post().uri("/auth/otra-cosa")
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

    /** FIN-52: el registro con Google crea un recurso, como el alta, pero responde con la sesion. */
    @Test
    void elRegistroConGoogleDevuelve201ConLaFormaDelLogin() {
        when(googleRegister.register(any())).thenReturn(Mono.just(SESION));

        webTestClient.post().uri(URI_GOOGLE_REGISTRO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("idToken", "un.id-token", "timezone", "Europe/Madrid"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("un.jwt.firmado")
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isEqualTo(3600)
                .jsonPath("$.refreshToken").isEqualTo("un-refresh-opaco")
                .jsonPath("$.refreshExpiresIn").isEqualTo(86400);

        verify(googleRegister).register(new GoogleRegistrationCommand("un.id-token", "Europe/Madrid"));
    }

    @Test
    void elLoginConGoogleDevuelve200ConLaFormaDelLogin() {
        when(googleLogin.login("un.id-token")).thenReturn(Mono.just(SESION));

        webTestClient.post().uri(URI_GOOGLE_LOGIN)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("idToken", "un.id-token"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("un.jwt.firmado")
                .jsonPath("$.refreshToken").isEqualTo("un-refresh-opaco");
    }

    @ParameterizedTest
    @ValueSource(strings = {URI_GOOGLE_REGISTRO, URI_GOOGLE_LOGIN})
    void sinIdTokenDevuelve400SinLlamarAlCasoDeUso(String ruta) {
        webTestClient.post().uri(ruta)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("idToken", "  "))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("idToken");

        verifyNoInteractions(googleRegister, googleLogin);
    }

    @Test
    void elRegistroConGoogleRechazaUnaZonaQueNoEsIana() {
        webTestClient.post().uri(URI_GOOGLE_REGISTRO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("idToken", "un.id-token", "timezone", "Marte/Olympus"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("timezone");

        verifyNoInteractions(googleRegister);
    }

    @Test
    void elRegistroConGoogleDevuelveLosErroresDelCasoDeUsoConSuStatus() {
        when(googleRegister.register(any())).thenReturn(Mono.error(new BadRequestException(
                HttpStatus.FORBIDDEN, ErrorCodes.REGISTRATION_NOT_ALLOWED,
                "El correo no esta admitido para registrarse", "email")));

        webTestClient.post().uri(URI_GOOGLE_REGISTRO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("idToken", "un.id-token"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("REGISTRATION_NOT_ALLOWED")
                .jsonPath("$.errors[0].field").isEqualTo("email");
    }

    /** El front ofrece el registro solo con este codigo: tiene que llegar tal cual. */
    @Test
    void elLoginConGoogleDevuelve404SiNoHayCuenta() {
        when(googleLogin.login(any())).thenReturn(Mono.error(new BadRequestException(
                HttpStatus.NOT_FOUND, ErrorCodes.ACCOUNT_NOT_REGISTERED,
                "No hay una cuenta registrada con esta cuenta de Google", "idToken")));

        webTestClient.post().uri(URI_GOOGLE_LOGIN)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("idToken", "un.id-token"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("ACCOUNT_NOT_REGISTERED")
                .jsonPath("$.errors[0].field").isEqualTo("idToken");
    }

    @ParameterizedTest
    @ValueSource(strings = {URI_GOOGLE_REGISTRO, URI_GOOGLE_LOGIN})
    void lasRutasDeGoogleExigenLaCredencialCompartida(String ruta) {
        webTestClient.post().uri(ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("idToken", "un.id-token"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Basic")
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("authorization");

        verifyNoInteractions(googleRegister, googleLogin);
    }

    @ParameterizedTest
    @ValueSource(strings = {URI_GOOGLE_REGISTRO, URI_GOOGLE_LOGIN})
    void unTokenValidoNoSirveEnLasRutasDeGoogle(String ruta) {
        webTestClient.post().uri(ruta)
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("idToken", "un.id-token"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Basic");

        verifyNoInteractions(googleRegister, googleLogin);
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
                new User(ID, UserMother.EMAIL, UserMother.HASH, "Ana", "Gomez", "USD", "America/Lima", null, null),
                22L);
    }
}
