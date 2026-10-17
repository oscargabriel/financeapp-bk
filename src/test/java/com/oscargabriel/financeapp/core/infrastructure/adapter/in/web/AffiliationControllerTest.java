package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import static com.oscargabriel.financeapp.core.support.AffiliateBalanceMother.PARTE_ID;
import static com.oscargabriel.financeapp.core.support.AffiliateBalanceMother.unSaldo;
import static com.oscargabriel.financeapp.core.support.AffiliateBalanceMother.unaParte;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.AFILIACION_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.CORREO_DESTINATARIO;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.unaSolicitud;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.unaVista;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.port.in.AffiliationPort;
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
    private AffiliationPort afiliaciones;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(UserMother.ID.toString()));
    }

    @Test
    void enviarRespondeCreadaConLaRelacionYElUsuarioDelToken() {
        AffiliationView enviada = AffiliationView.sent(unaSolicitud().build());
        when(afiliaciones.send(UserMother.ID, "  Beto@Ejemplo.com ")).thenReturn(Mono.just(enviada));

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

        verifyNoInteractions(afiliaciones);
    }

    @Test
    void listarDevuelveLasRelacionesConLaOtraPersona() {
        when(afiliaciones.list(UserMother.ID)).thenReturn(Flux.just(unaVista().build()));

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
        when(afiliaciones.accept(UserMother.ID, AFILIACION_ID))
                .thenReturn(Mono.just(unaVista().conEstado(AffiliationStatus.ACCEPTED).build()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/" + AFILIACION_ID + "/accept")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED");
    }

    static Stream<Arguments> lasQueSacanLaRelacionDelListado() {
        return Stream.of(
                Arguments.of("reject", AffiliationStatus.REJECTED,
                        (Function<AffiliationPort, Mono<AffiliationView>>) port -> port.reject(UserMother.ID, AFILIACION_ID)),
                Arguments.of("cancel", AffiliationStatus.CANCELLED,
                        (Function<AffiliationPort, Mono<AffiliationView>>) port -> port.cancel(UserMother.ID, AFILIACION_ID)),
                Arguments.of("unlink", AffiliationStatus.UNLINKED,
                        (Function<AffiliationPort, Mono<AffiliationView>>) port -> port.unlink(UserMother.ID, AFILIACION_ID)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lasQueSacanLaRelacionDelListado")
    void lasQueSacanLaRelacionDelListadoRespondenSinCuerpo(String ruta, AffiliationStatus estado,
            Function<AffiliationPort, Mono<AffiliationView>> operacion) {
        when(operacion.apply(afiliaciones)).thenReturn(Mono.just(unaVista().conEstado(estado).build()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/" + AFILIACION_ID + "/" + ruta)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        operacion.apply(verify(afiliaciones));
    }

    @Test
    void unIdQueNoEsUuidEs400SobreId() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/abc/accept")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verifyNoInteractions(afiliaciones);
    }

    @Test
    void elSaldoTraeCadaPersonaConSusMonedasYElNeto() {
        when(afiliaciones.balances(UserMother.ID)).thenReturn(Flux.just(unSaldo().build()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/balances")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].affiliationId").isEqualTo(AFILIACION_ID.toString())
                .jsonPath("$[0].status").isEqualTo("ACCEPTED")
                .jsonPath("$[0].person.email").isEqualTo(CORREO_DESTINATARIO)
                .jsonPath("$[0].person.firstName").isEqualTo("Beto")
                .jsonPath("$[0].person.lastName").isEqualTo("Debe")
                .jsonPath("$[0].balances.length()").isEqualTo(1)
                .jsonPath("$[0].balances[0].currencyCode").isEqualTo("COP")
                .jsonPath("$[0].balances[0].owedToMe").isEqualTo(30000)
                .jsonPath("$[0].balances[0].iOwe").isEqualTo(10000)
                .jsonPath("$[0].balances[0].net").isEqualTo(20000);
    }

    @Test
    void unaPersonaAPazYSalvoSaleConLaListaDeMonedasVacia() {
        when(afiliaciones.balances(UserMother.ID)).thenReturn(Flux.just(unSaldo().aPazYSalvo().build()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/balances")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].balances").isEmpty();
    }

    @Test
    void sinPersonasElSaldoEsUnaListaVacia() {
        when(afiliaciones.balances(UserMother.ID)).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/balances")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    @Test
    void elDetalleTraeCadaParteConSuSentido() {
        when(afiliaciones.shares(UserMother.ID, AFILIACION_ID))
                .thenReturn(Flux.just(unaParte().pagada().build(), unaParte().queDebo().build()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/" + AFILIACION_ID + "/shares")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo(PARTE_ID.toString())
                .jsonPath("$[0].description").isEqualTo("Cena")
                .jsonPath("$[0].occurredAt").isEqualTo("2026-10-13T17:30:00Z")
                .jsonPath("$[0].amount").isEqualTo(30000)
                .jsonPath("$[0].currencyCode").isEqualTo("COP")
                .jsonPath("$[0].status").isEqualTo("PAID")
                .jsonPath("$[0].resolvedAt").isEqualTo("2026-10-14T09:00:00Z")
                .jsonPath("$[0].direction").isEqualTo("OWED_TO_ME")
                .jsonPath("$[1].status").isEqualTo("PENDING")
                .jsonPath("$[1].resolvedAt").isEqualTo(null)
                .jsonPath("$[1].direction").isEqualTo("I_OWE");
    }

    @Test
    void elDetalleConUnIdQueNoEsUuidEs400SobreId() {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/abc/shares")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verifyNoInteractions(afiliaciones);
    }
}
