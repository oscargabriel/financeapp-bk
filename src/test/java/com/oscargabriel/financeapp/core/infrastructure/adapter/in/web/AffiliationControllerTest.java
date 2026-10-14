package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import static com.oscargabriel.financeapp.core.support.AffiliationMother.AFILIACION_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.CORREO_DESTINATARIO;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.unaSolicitud;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.unaVista;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.compartido.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.compartido.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.compartido.support.SesionSiempreAbierta;
import com.oscargabriel.financeapp.core.domain.model.AffiliationAction;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.port.in.ChangeAffiliationPort;
import com.oscargabriel.financeapp.core.domain.port.in.ListAffiliationsPort;
import com.oscargabriel.financeapp.core.domain.port.in.SendAffiliationRequestPort;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre AffiliationsIT. */
@WebFluxTest(AffiliationController.class)
@Import({SecurityConfig.class, JwtConfig.class, SesionSiempreAbierta.class})
class AffiliationControllerTest {

    private static final String URI_BASE = "/affiliations";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SendAffiliationRequestPort sendRequest;

    @MockitoBean
    private ListAffiliationsPort listAffiliations;

    @MockitoBean
    private ChangeAffiliationPort changeAffiliation;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(UserMother.ID.toString()));
    }

    @Test
    void enviarRespondeCreadaConLaRelacionYElUsuarioDelToken() {
        AffiliationView enviada = AffiliationView.sent(unaSolicitud().build());
        when(sendRequest.send(UserMother.ID, "  Beto@Ejemplo.com ")).thenReturn(Mono.just(enviada));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\": \"  Beto@Ejemplo.com \"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(AFILIACION_ID.toString())
                .jsonPath("$.status").isEqualTo("REQUESTED")
                .jsonPath("$.direction").isEqualTo("SENT")
                .jsonPath("$.person.email").isEqualTo(CORREO_DESTINATARIO)
                .jsonPath("$.person.firstName").isEqualTo(null)
                .jsonPath("$.person.lastName").isEqualTo(null)
                .jsonPath("$.createdAt").isEqualTo("2026-10-13T15:00:00Z")
                .jsonPath("$.updatedAt").isEqualTo("2026-10-13T15:00:00Z");
    }

    @ParameterizedTest(name = "cuerpo {0}")
    @CsvSource(delimiter = '|', value = {"{}", "{\"email\": \"\"}", "{\"email\": \"no-es-correo\"}"})
    void unCorreoInvalidoEs400SobreEmailSinLlegarAlCasoDeUso(String cuerpo) {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cuerpo)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("email");

        verifyNoInteractions(sendRequest);
    }

    @Test
    void listarDevuelveLasRelacionesConLaOtraPersona() {
        when(listAffiliations.list(UserMother.ID)).thenReturn(Flux.just(unaVista().build()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].direction").isEqualTo("RECEIVED")
                .jsonPath("$[0].person.email").isEqualTo(UserMother.EMAIL)
                .jsonPath("$[0].person.firstName").isEqualTo("Ana")
                .jsonPath("$[0].person.lastName").isEqualTo("Gomez");
    }

    @Test
    void aceptarRespondeLaRelacionAceptada() {
        when(changeAffiliation.change(UserMother.ID, AFILIACION_ID, AffiliationAction.ACCEPT))
                .thenReturn(Mono.just(unaVista().conEstado(AffiliationStatus.ACCEPTED).build()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/" + AFILIACION_ID + "/accept")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"reject, REJECT", "cancel, CANCEL", "unlink, UNLINK"})
    void lasQueSacanLaRelacionDelListadoRespondenSinCuerpo(String ruta, AffiliationAction accion) {
        when(changeAffiliation.change(UserMother.ID, AFILIACION_ID, accion))
                .thenReturn(Mono.just(unaVista().conEstado(accion.to()).build()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/" + AFILIACION_ID + "/" + ruta)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(changeAffiliation).change(UserMother.ID, AFILIACION_ID, accion);
    }

    @Test
    void unIdQueNoEsUuidEs400SobreId() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/abc/accept")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verify(changeAffiliation, never()).change(any(), any(), any());
    }
}
