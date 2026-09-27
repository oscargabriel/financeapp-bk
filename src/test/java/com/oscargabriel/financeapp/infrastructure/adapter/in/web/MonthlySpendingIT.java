package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.support.TokenMother;

/**
 * Servidor real: verifica que la ruta queda publicada bajo spring.webflux.base-path y protegida.
 * El camino con datos no se prueba aqui porque el R2DBC de la suite apunta a un puerto sin
 * escucha a proposito. Desde que Testcontainers salio de la suite, el 200 con cuerpo, el calculo
 * de la meta y el corte de mes por zona horaria los verifica solo bruno/monthly-spending/ contra
 * el servidor levantado: esta clase cubre lo que se puede probar sin base.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MonthlySpendingIT {

    private static final String RUTA = "/api/monthly-spending";

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
        webTestClient.get().uri("/monthly-spending")
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Con el token valido la cadena del JWT deja pasar y es el enrutamiento el que responde: 404,
     * porque desde FA-15 nadie publica esa ruta. Sin token daria 401 y no probaria nada, que es lo
     * que hace el primer caso de esta clase sobre la ruta nueva.
     */
    @Test
    void laRutaConUserIdEnLaUrlYaNoExiste() {
        webTestClient.get().uri("/api/users/10000000-0000-7000-8000-000000000001/monthly-spending")
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND");
    }
}
