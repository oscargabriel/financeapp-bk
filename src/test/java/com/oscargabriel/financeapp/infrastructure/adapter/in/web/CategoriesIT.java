package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.support.BasicMother;
import com.oscargabriel.financeapp.support.TokenMother;

/**
 * Servidor real: la ruta queda bajo spring.webflux.base-path y protegida por la cadena del JWT. El
 * camino con datos lo verifica bruno/categories/ contra PostgreSQL, por la misma razon que
 * MonthlySpendingIT: el R2DBC de la suite apunta a un puerto sin escucha.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoriesIT {

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
        webTestClient.get().uri("/api/categories")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer");
    }

    @Test
    void noExponeElEndpointFueraDelBasePath() {
        webTestClient.get().uri("/categories")
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void elAltaSinCredencialesEsUn401ConElRetoDelJwt() {
        webTestClient.post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"Plantas\", \"appliesTo\": \"EXPENSE\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elAltaConElBasicCompartidoEsUn401() {
        webTestClient.post().uri("/api/categories")
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"Plantas\", \"appliesTo\": \"EXPENSE\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elPatchSinCredencialesEsUn401ConElRetoDelJwt() {
        webTestClient.patch().uri("/api/categories/30000000-0000-7000-8000-000000000004")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"Huerta\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elPatchConElBasicCompartidoEsUn401() {
        webTestClient.patch().uri("/api/categories/30000000-0000-7000-8000-000000000004")
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"Huerta\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elBorradoSinCredencialesEsUn401ConElRetoDelJwt() {
        webTestClient.delete().uri("/api/categories/30000000-0000-7000-8000-000000000004")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elBorradoConElBasicCompartidoEsUn401() {
        webTestClient.delete().uri("/api/categories/30000000-0000-7000-8000-000000000004")
                .headers(BasicMother.cabecera())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }
}
