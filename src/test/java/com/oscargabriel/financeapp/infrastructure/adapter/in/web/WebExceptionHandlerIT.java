package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebExceptionHandlerIT {

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
    void devuelveElFormatoDeErrorEstandarEnUnaRutaInexistente() {
        webTestClient.get().uri("/api/no-existe")
                .headers(headers -> headers.setBasicAuth("test", "test"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errors").isArray()
                .jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND")
                .jsonPath("$.errors[0].description").isNotEmpty()
                .jsonPath("$.errors[0].field").isEqualTo("request");
    }

    @Test
    void noFiltraInternalsEnElCuerpoDelError() {
        webTestClient.get().uri("/api/no-existe")
                .headers(headers -> headers.setBasicAuth("test", "test"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.trace").doesNotExist()
                .jsonPath("$.exception").doesNotExist()
                .jsonPath("$.path").doesNotExist();
    }

    @Test
    void devuelve401SinCredencialesAntesDeLlegarAlHandler() {
        webTestClient.get().uri("/api/no-existe")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
