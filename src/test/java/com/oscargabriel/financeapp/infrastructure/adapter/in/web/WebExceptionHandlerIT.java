package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.support.TokenMother;

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
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
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
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.trace").doesNotExist()
                .jsonPath("$.exception").doesNotExist()
                .jsonPath("$.path").doesNotExist();
    }

    /**
     * El codec corta el cuerpo antes de que exista un DTO. La suite no replica el 1 MB de
     * application.yaml, asi que aqui el limite es el de Spring por defecto (256 KB): basta con pasar
     * del MB para superar los dos.
     */
    @Test
    void devuelve413ConUnCodigoPropioCuandoElCuerpoSuperaElLimiteDelCodec() {
        webTestClient.post().uri("/api/accounts")
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"" + "x".repeat(1_100_000) + "\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("PAYLOAD_TOO_LARGE")
                .jsonPath("$.errors[0].field").isEqualTo("body")
                .jsonPath("$.errors[0].description").isNotEmpty();
    }

    @Test
    void devuelve401SinCredencialesAntesDeLlegarAlHandler() {
        webTestClient.get().uri("/api/no-existe")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
