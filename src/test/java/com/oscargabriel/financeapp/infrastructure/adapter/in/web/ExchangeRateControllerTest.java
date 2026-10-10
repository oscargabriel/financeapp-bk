package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.ExchangeRate;
import com.oscargabriel.financeapp.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.CategoryMother;

import reactor.core.publisher.Mono;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre ExchangeRatesIT. */
@WebFluxTest(ExchangeRateController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class ExchangeRateControllerTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 10);

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ResolveExchangeRatePort resolveExchangeRate;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(CategoryMother.USER_ID.toString()));
    }

    @Test
    void devuelveLaTasaConLaFechaPedidaYLaDeLaFilaUsada() {
        when(resolveExchangeRate.resolve("EUR", "COP", HOY)).thenReturn(Mono.just(
                new ExchangeRate("EUR", "COP", HOY, new BigDecimal("5125.0000000000"), HOY.minusDays(1))));

        webTestClient.mutateWith(tokenDelUsuario()).get()
                .uri("/exchange-rates?from=EUR&to=COP&date=2026-10-10")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {"from": "EUR", "to": "COP", "date": "2026-10-10", "rate": 5125.0000000000,
                         "rateDate": "2026-10-09"}
                        """, true);
    }

    @Test
    void propagaElErrorDelCasoDeUso() {
        when(resolveExchangeRate.resolve("USD", "COP", LocalDate.of(2000, 1, 1))).thenReturn(Mono.error(
                new BadRequestException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "No hay tasa", "date")));

        webTestClient.mutateWith(tokenDelUsuario()).get()
                .uri("/exchange-rates?from=USD&to=COP&date=2000-01-01")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND")
                .jsonPath("$.errors[0].field").isEqualTo("date");
    }

    @ParameterizedTest
    @CsvSource({
            "'to=COP&date=2026-10-10', from",
            "'from=&to=COP&date=2026-10-10', from",
            "'from=USD&date=2026-10-10', to",
            "'from=USD&to=COP', date",
            "'from=USD&to=COP&date=10-10-2026', date",
            "'from=USD&to=COP&date=2026-02-30', date"
    })
    void devuelve400EnElCampoDelParametroFaltanteOMalFormado(String consulta, String campo) {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri("/exchange-rates?" + consulta)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo(campo);

        verifyNoInteractions(resolveExchangeRate);
    }

    @Test
    void devuelve401CuandoNoHayCredenciales() {
        webTestClient.get().uri("/exchange-rates?from=USD&to=COP&date=2026-10-10")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(resolveExchangeRate);
    }
}
