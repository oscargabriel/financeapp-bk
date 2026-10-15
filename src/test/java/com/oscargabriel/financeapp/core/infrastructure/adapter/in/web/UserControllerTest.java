package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.oscargabriel.financeapp.core.domain.model.ChangePasswordCommand;
import com.oscargabriel.financeapp.core.domain.model.FirstPasswordCommand;
import com.oscargabriel.financeapp.core.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.core.domain.port.in.ChangePasswordPort;
import com.oscargabriel.financeapp.core.domain.port.in.GetUserProfilePort;
import com.oscargabriel.financeapp.core.domain.port.in.SetFirstPasswordPort;
import com.oscargabriel.financeapp.core.domain.port.in.UpdateUserProfilePort;
import com.oscargabriel.financeapp.compartido.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.compartido.support.SesionSiempreAbierta;
import com.oscargabriel.financeapp.compartido.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Mono;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre UsersIT. */
@WebFluxTest(UserController.class)
@Import({SecurityConfig.class, JwtConfig.class, SesionSiempreAbierta.class})
class UserControllerTest {

    private static final String URI_PERFIL = "/users/me";

    private static final String URI_CLAVE = "/users/me/password";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GetUserProfilePort getProfile;

    @MockitoBean
    private UpdateUserProfilePort updateProfile;

    @MockitoBean
    private ChangePasswordPort changePassword;

    @MockitoBean
    private SetFirstPasswordPort setFirstPassword;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(UserMother.ID.toString()));
    }

    @Test
    void devuelve401CuandoNoHayCredenciales() {
        webTestClient.get().uri(URI_PERFIL)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(getProfile);
    }

    @Test
    void devuelveElPerfilDelUsuarioDelTokenConElFormatoDelContrato() {
        when(getProfile.get(UserMother.ID)).thenReturn(Mono.just(UserMother.unPerfil()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_PERFIL)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(UserMother.ID.toString())
                .jsonPath("$.email").isEqualTo(UserMother.EMAIL)
                .jsonPath("$.firstName").isEqualTo("Ana")
                .jsonPath("$.lastName").isEqualTo(null)
                .jsonPath("$.phone").isEqualTo("3001234567")
                .jsonPath("$.baseCurrencyCode").isEqualTo("COP")
                .jsonPath("$.timezone").isEqualTo("America/Bogota")
                .jsonPath("$.hasPassword").isEqualTo(true)
                .jsonPath("$.googleLinked").isEqualTo(false);
    }

    /** FIN-53: el front decide con estos dos campos si ofrece definir la primera contrasena. */
    @Test
    void elPerfilDeUnUsuarioDeGoogleDiceQueNoTieneContrasena() {
        when(getProfile.get(UserMother.ID)).thenReturn(Mono.just(UserMother.unPerfilDeGoogle()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_PERFIL)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.hasPassword").isEqualTo(false)
                .jsonPath("$.googleLinked").isEqualTo(true);
    }

    /** OWASP A02: el perfil nunca lleva la contrasena ni su hash, ni bajo otro nombre. */
    @Test
    void nuncaDevuelveLaContrasenaNiSuHash() {
        when(getProfile.get(UserMother.ID)).thenReturn(Mono.just(UserMother.unPerfil()));

        byte[] cuerpo = webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_PERFIL)
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();

        assertThat(new String(cuerpo)).doesNotContain("password").doesNotContain("Hash");
    }

    @Test
    void elPatchDevuelveElPerfilYTrasladaElParcheTalCual() {
        when(updateProfile.update(any(), any())).thenReturn(Mono.just(UserMother.unPerfil()));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PERFIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"firstName": " Ana ", "lastName": "", "email": "Ana@Ejemplo.com", "phone": "3001234567",
                         "timezone": "America/Bogota", "baseCurrencyCode": " usd ", "currentPassword": "unaClaveLarga"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(UserMother.ID.toString())
                .jsonPath("$.firstName").isEqualTo("Ana")
                .jsonPath("$.hasPassword").isEqualTo(true)
                .jsonPath("$.googleLinked").isEqualTo(false);

        verify(updateProfile).update(UserMother.ID, new UpdateUserProfileCommand(
                " Ana ", "", "Ana@Ejemplo.com", "3001234567", "America/Bogota", " usd ", "unaClaveLarga"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"firstName\": null}", "{\"currentPassword\": \"unaClaveLarga\"}"})
    void unParcheSinCamposModificablesEsUn400EnBody(String cuerpo) {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PERFIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cuerpo)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("body");

        verifyNoInteractions(updateProfile);
    }

    /** La moneda cuenta como campo modificable (FIN-29): sola no es un parche vacio. */
    @Test
    void laMonedaBaseSolaLlegaAlCasoDeUso() {
        when(updateProfile.update(any(), any())).thenReturn(Mono.just(UserMother.unPerfil()));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PERFIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"baseCurrencyCode\": \"USD\"}")
                .exchange()
                .expectStatus().isOk();

        verify(updateProfile).update(UserMother.ID,
                new UpdateUserProfileCommand(null, null, null, null, null, "USD", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"  ", "US", "U5D", "USDX"})
    void unaMonedaEnBlancoOMalFormadaEsUn400EnSuCampo(String moneda) {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PERFIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"baseCurrencyCode\": \"" + moneda + "\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("baseCurrencyCode");

        verifyNoInteractions(updateProfile);
    }

    @Test
    void unCampoMalFormadoEsUn400SinLlamarAlCasoDeUso() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PERFIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"phone\": \"12-34\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("phone");

        verifyNoInteractions(updateProfile);
    }

    @Test
    void elPatchSinCredencialesEsUn401() {
        webTestClient.patch().uri(URI_PERFIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"firstName\": \"Ana\"}")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(updateProfile);
    }

    @Test
    void cambiarLaClaveEsUn204SinCuerpo() {
        when(changePassword.change(any(), any())).thenReturn(Mono.empty());

        webTestClient.mutateWith(tokenDelUsuario()).put().uri(URI_CLAVE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPassword\": \"unaClaveLarga\", \"newPassword\": \"otraClaveLarga\"}")
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(changePassword).change(UserMother.ID, new ChangePasswordCommand("unaClaveLarga", "otraClaveLarga"));
    }

    @Test
    void unaClaveNuevaCortaEsUn400SinLlamarAlCasoDeUso() {
        webTestClient.mutateWith(tokenDelUsuario()).put().uri(URI_CLAVE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPassword\": \"unaClaveLarga\", \"newPassword\": \"corta12\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("newPassword");

        verifyNoInteractions(changePassword);
    }

    /** 400 y no 401: un 401 haria que el front cerrara una sesion que sigue siendo valida. */
    @Test
    void laClaveActualEquivocadaSaleComo400() {
        when(changePassword.change(any(), any())).thenReturn(Mono.error(new BadRequestException(
                HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_CREDENTIALS, "La contrasena actual no es correcta",
                "currentPassword")));

        webTestClient.mutateWith(tokenDelUsuario()).put().uri(URI_CLAVE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPassword\": \"noEsLaClave\", \"newPassword\": \"otraClaveLarga\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("INVALID_CREDENTIALS")
                .jsonPath("$.errors[0].field").isEqualTo("currentPassword");
    }

    @Test
    void cambiarLaClaveSinCredencialesEsUn401() {
        webTestClient.put().uri(URI_CLAVE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPassword\": \"unaClaveLarga\", \"newPassword\": \"otraClaveLarga\"}")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(changePassword);
    }

    @Test
    void definirLaPrimeraClaveEsUn204SinCuerpo() {
        when(setFirstPassword.define(any(), any())).thenReturn(Mono.empty());

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_CLAVE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"idToken\": \"un.id-token.de-google\", \"newPassword\": \"primeraClaveLarga\"}")
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(setFirstPassword).define(UserMother.ID,
                new FirstPasswordCommand("un.id-token.de-google", "primeraClaveLarga"));
    }

    @Test
    void definirLaPrimeraClaveSinCamposEsUn400EnCadaUno() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_CLAVE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[?(@.field == 'idToken')].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[?(@.field == 'newPassword')].code").isEqualTo("VALIDATION_ERROR");

        verifyNoInteractions(setFirstPassword);
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta12", "unaClaveDeMasDeSetentaYDosBytesUnaClaveDeMasDeSetentaYDosBytesUnaClaveDeMas"})
    void unaPrimeraClaveFueraDeLasReglasEsUn400(String clave) {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_CLAVE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"idToken\": \"un.id-token.de-google\", \"newPassword\": \"" + clave + "\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("newPassword");

        verifyNoInteractions(setFirstPassword);
    }

    /** Los errores del caso de uso salen con su status: 400 y no 401 para el token (decision 3 de FIN-53). */
    @ParameterizedTest
    @CsvSource({
        "BAD_REQUEST, INVALID_CREDENTIALS, idToken",
        "CONFLICT, INVALID_STATE, newPassword",
        "BAD_GATEWAY, EXTERNAL_SERVICE_ERROR, idToken"
    })
    void losErroresDeLaPrimeraClaveSalenConSuStatus(HttpStatus status, ErrorCodes codigo, String campo) {
        when(setFirstPassword.define(any(), any())).thenReturn(Mono.error(
                new BadRequestException(status, codigo, "rechazado", campo)));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_CLAVE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"idToken\": \"un.id-token.de-google\", \"newPassword\": \"primeraClaveLarga\"}")
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(codigo.getCode())
                .jsonPath("$.errors[0].field").isEqualTo(campo);
    }

    @Test
    void elUsuarioInactivoSaleComo401ConElFormatoDelProyecto() {
        when(getProfile.get(UserMother.ID)).thenReturn(Mono.error(new BadRequestException(
                HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, "Autenticacion requerida", "authorization")));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_PERFIL)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }
}
