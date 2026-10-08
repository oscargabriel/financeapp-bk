package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.support.BasicMother;
import com.oscargabriel.financeapp.support.TokenMother;

/**
 * Servidor real: la ruta queda bajo spring.webflux.base-path y protegida por la cadena del JWT. El
 * alta con datos la verifica bruno/transactions/ contra PostgreSQL: el R2DBC de la suite apunta a un
 * puerto sin escucha.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionsIT {

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
        webTestClient.post().uri("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[]")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer");
    }

    @Test
    void noExponeElEndpointFueraDelBasePath() {
        webTestClient.post().uri("/transactions")
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[]")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void rechazaElLoteVacioAntesDeConsultarLaBase() {
        webTestClient.post().uri("/api/transactions")
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[]")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("body");
    }

    private static final String MOVIMIENTO = "/api/transactions/50000000-0000-7000-8000-000000000001";

    @Test
    void elPatchSinCredencialesEsUn401ConElRetoDelJwt() {
        webTestClient.patch().uri(MOVIMIENTO)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"description\": \"Mercado\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elPatchConElBasicCompartidoEsUn401() {
        webTestClient.patch().uri(MOVIMIENTO)
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"description\": \"Mercado\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void elDeleteSinCredencialesEsUn401ConElRetoDelJwt() {
        webTestClient.delete().uri(MOVIMIENTO)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elDeleteConElBasicCompartidoEsUn401() {
        webTestClient.delete().uri(MOVIMIENTO)
                .headers(BasicMother.cabecera())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED");
    }

    /** Los tres endpoints de pendientes caen en la cadena del JWT, sin regla propia. */
    @ParameterizedTest
    @CsvSource({
            "GET,  /api/transactions/pending",
            "POST, /api/transactions/50000000-0000-7000-8000-000000000001/approve",
            "POST, /api/transactions/50000000-0000-7000-8000-000000000001/reject"})
    void losPendientesSinCredencialesSonUn401ConElRetoDelJwt(HttpMethod metodo, String ruta) {
        webTestClient.method(metodo).uri(ruta)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @ParameterizedTest
    @CsvSource({
            "GET,  /api/transactions/pending",
            "POST, /api/transactions/50000000-0000-7000-8000-000000000001/approve",
            "POST, /api/transactions/50000000-0000-7000-8000-000000000001/reject"})
    void losPendientesConElBasicCompartidoSonUn401(HttpMethod metodo, String ruta) {
        webTestClient.method(metodo).uri(ruta)
                .headers(BasicMother.cabecera())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED");
    }

    /** Sin PATCH en la politica CORS, un navegador no llegaria a enviar la modificacion. */
    @Test
    void elPreflightDeUnPatchLoAutoriza() {
        webTestClient.options().uri(MOVIMIENTO)
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PATCH")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("Access-Control-Allow-Methods",
                        metodos -> assertThat(metodos).contains("PATCH"));
    }
}
