package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.in.GetMonthlySpendingPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.MonthlySpendingMother;

import reactor.core.publisher.Flux;

/**
 * El slice monta el controller sin el base-path /api, igual que StatusControllerTest: la ruta
 * completa la verifica MonthlySpendingIT contra un servidor real.
 */
@WebFluxTest(MonthlySpendingController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class MonthlySpendingControllerTest {

    private static final String URI_BASE = "/monthly-spending";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GetMonthlySpendingPort getMonthlySpending;

    /** El userId ya no viaja en la URL: el controlador lo lee del subject del token. */
    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(MonthlySpendingMother.USER_ID.toString()));
    }

    @Test
    void devuelve401CuandoNoHayCredenciales() {
        webTestClient.get().uri(URI_BASE)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(getMonthlySpending);
    }

    @Test
    void devuelveLosMesesConElFormatoDelContrato() {
        when(getMonthlySpending.get(eq(MonthlySpendingMother.USER_ID), any(), any())).thenReturn(
                Flux.just(MonthlySpendingMother.unMesConMeta(YearMonth.of(2026, 9))));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].periodMonth").isEqualTo("2026-09")
                .jsonPath("$[0].currencyCode").isEqualTo("COP")
                .jsonPath("$[0].totalSpent").isEqualTo(1905500.00)
                .jsonPath("$[0].budgetAmount").isEqualTo(2000000.00)
                .jsonPath("$[0].remaining").isEqualTo(94500.00)
                .jsonPath("$[0].percentUsed").isEqualTo(95.28)
                .jsonPath("$[0].transactionCount").isEqualTo(13)
                .jsonPath("$[0].userId").doesNotExist();
    }

    @Test
    void devuelveUnArrayVacioCuandoElUsuarioNoTieneMeses() {
        when(getMonthlySpending.get(eq(MonthlySpendingMother.USER_ID), any(), any()))
                .thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    @Test
    void mantieneEnNullLosCamposDeMetaCuandoElMesNoTieneMeta() {
        when(getMonthlySpending.get(eq(MonthlySpendingMother.USER_ID), any(), any())).thenReturn(
                Flux.just(MonthlySpendingMother.unMesSinMeta(YearMonth.of(2026, 8))));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].budgetAmount").isEqualTo(null)
                .jsonPath("$[0].remaining").isEqualTo(null)
                .jsonPath("$[0].percentUsed").isEqualTo(null)
                .jsonPath("$[0].totalSpent").isEqualTo(320000.00);
    }

    @Test
    void pasaAlCasoDeUsoLosMesesParseadosDeLaQuery() {
        when(getMonthlySpending.get(eq(MonthlySpendingMother.USER_ID), any(), any()))
                .thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get()
                .uri(URI_BASE + "?from=2026-01&to=2026-03")
                .exchange()
                .expectStatus().isOk();

        verify(getMonthlySpending).get(
                MonthlySpendingMother.USER_ID, YearMonth.of(2026, 1), YearMonth.of(2026, 3));
    }

    @Test
    void pasaNullAlCasoDeUsoCuandoNoLleganLosMeses() {
        when(getMonthlySpending.get(eq(MonthlySpendingMother.USER_ID), any(), any()))
                .thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk();

        verify(getMonthlySpending).get(MonthlySpendingMother.USER_ID, null, null);
    }

    /**
     * Solo alcanzable con un token firmado con la clave de la aplicacion y un subject que no emite
     * JwtTokenIssuerAdapter. Un sub que no identifica a nadie no autentica, asi que sale como el
     * mismo 401 de UnauthenticatedEntryPoint y no como un 400 sobre un parametro que el cliente no
     * controla.
     */
    @Test
    void devuelve401CuandoElSubjectDelTokenNoEsUnUuid() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("no-es-uuid")))
                .get().uri(URI_BASE)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.UNAUTHENTICATED.getCode())
                .jsonPath("$.errors[0].description").isEqualTo("Autenticacion requerida")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");

        verifyNoInteractions(getMonthlySpending);
    }

    @Test
    void devuelve400CuandoElMesInicialNoTieneElFormatoEsperado() {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?from=2026-9")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("from");

        verifyNoInteractions(getMonthlySpending);
    }

    @Test
    void devuelve400CuandoElMesFinalNoTieneElFormatoEsperado() {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?to=septiembre")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("to");

        verifyNoInteractions(getMonthlySpending);
    }

    @Test
    void propagaConElFormatoEstandarElErrorDeRangoDelCasoDeUso() {
        when(getMonthlySpending.get(eq(MonthlySpendingMother.USER_ID), any(), any())).thenReturn(
                Flux.error(new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                        "El mes inicial es posterior al mes final", "from")));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?from=2026-05&to=2026-01")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("from");
    }

}
