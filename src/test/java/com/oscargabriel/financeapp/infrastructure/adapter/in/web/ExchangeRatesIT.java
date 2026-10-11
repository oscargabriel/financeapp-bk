package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.support.BasicMother;
import com.oscargabriel.financeapp.support.TokenMother;

/**
 * Servidor real: la ruta queda bajo spring.webflux.base-path y protegida por la cadena del JWT. Con
 * un token valido la consulta toca la base, asi que el camino feliz lo verifica bruno/exchange-rates/.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExchangeRatesIT {

    private static final String CONSULTA = "/exchange-rates?from=USD&to=COP&date=2026-10-10";

    @Value("${local.server.port}")
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    void devuelve401EnLaRutaConBasePathCuandoNoHayCredenciales() {
        webTestClient.get().uri("/api" + CONSULTA)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer");
    }

    @Test
    void devuelve401ConLaCredencialBasic() {
        webTestClient.get().uri("/api" + CONSULTA)
                .headers(BasicMother.cabecera())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void noExponeElEndpointFueraDelBasePath() {
        webTestClient.get().uri(CONSULTA)
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .exchange()
                .expectStatus().isNotFound();
    }
}
