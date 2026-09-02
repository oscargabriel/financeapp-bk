package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Servidor real: el unico punto donde se verifica que spring.webflux.base-path aplica (lo hace el
 * ContextPathCompositeHandler, que el WebTestClient enlazado al contexto se saltaria) y que el
 * adaptador de PostgreSQL reporta DOWN cuando la base no responde.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatusIT {

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
        webTestClient.get().uri("/api/status")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void devuelve503ConPostgresAbajoCuandoLaBaseNoResponde() {
        webTestClient.get().uri("/api/status")
                .headers(headers -> headers.setBasicAuth("test", "test"))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo("DOWN")
                .jsonPath("$.services.postgres").isEqualTo("DOWN");
    }

    @Test
    void noExponeElEndpointFueraDelBasePath() {
        webTestClient.get().uri("/status")
                .headers(headers -> headers.setBasicAuth("test", "test"))
                .exchange()
                .expectStatus().isNotFound();
    }
}
