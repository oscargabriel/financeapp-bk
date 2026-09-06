package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Servidor real: verifica que la ruta queda publicada bajo spring.webflux.base-path y protegida.
 * El camino con datos no se prueba aqui porque el R2DBC de la suite apunta a un puerto sin
 * escucha a proposito; eso lo cubre MonthlySpendingR2dbcAdapterIT contra PostgreSQL real y el
 * request de Bruno contra el servidor levantado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MonthlySpendingIT {

    private static final String RUTA = "/api/users/10000000-0000-7000-8000-000000000001/monthly-spending";

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
        webTestClient.get().uri(RUTA)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void noExponeElEndpointFueraDelBasePath() {
        webTestClient.get().uri("/users/10000000-0000-7000-8000-000000000001/monthly-spending")
                .headers(headers -> headers.setBasicAuth("test", "test"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void rechazaConBadRequestUnUserIdQueNoEsUuidAntesDeTocarLaBase() {
        webTestClient.get().uri("/api/users/no-es-uuid/monthly-spending")
                .headers(headers -> headers.setBasicAuth("test", "test"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("INVALID_ARGUMENT")
                .jsonPath("$.errors[0].field").isEqualTo("userId");
    }
}
