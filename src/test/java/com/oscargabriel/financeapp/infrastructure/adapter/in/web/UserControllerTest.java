package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.ChangePasswordCommand;
import com.oscargabriel.financeapp.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.domain.port.in.ChangePasswordPort;
import com.oscargabriel.financeapp.domain.port.in.GetUserProfilePort;
import com.oscargabriel.financeapp.domain.port.in.UpdateUserProfilePort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.UserMother;

import reactor.core.publisher.Mono;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre UsersIT. */
@WebFluxTest(UserController.class)
@Import({SecurityConfig.class, JwtConfig.class})
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
                .jsonPath("$.timezone").isEqualTo("America/Bogota");
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
                         "timezone": "America/Bogota", "currentPassword": "unaClaveLarga"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(UserMother.ID.toString())
                .jsonPath("$.firstName").isEqualTo("Ana");

        verify(updateProfile).update(UserMother.ID, new UpdateUserProfileCommand(
                " Ana ", "", "Ana@Ejemplo.com", "3001234567", "America/Bogota", "unaClaveLarga"));
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

    /** La moneda se omite como cualquier propiedad desconocida: no es un error de deserializacion. */
    @Test
    void laMonedaBaseSolaSeOmiteYElParcheQuedaVacio() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PERFIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"baseCurrencyCode\": \"USD\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("body");

        verifyNoInteractions(updateProfile);
    }

    @Test
    void laMonedaBaseJuntoAOtrosCamposNoLlegaAlCasoDeUso() {
        when(updateProfile.update(any(), any())).thenReturn(Mono.just(UserMother.unPerfil()));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_PERFIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"firstName\": \"Ana\", \"baseCurrencyCode\": \"USD\"}")
                .exchange()
                .expectStatus().isOk();

        verify(updateProfile).update(UserMother.ID,
                new UpdateUserProfileCommand("Ana", null, null, null, null, null));
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
