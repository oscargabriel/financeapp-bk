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
 * camino con datos lo verifica bruno/accounts/ contra PostgreSQL, por la misma razon que
 * CategoriesIT: el R2DBC de la suite apunta a un puerto sin escucha.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountsIT {

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
        webTestClient.get().uri("/api/accounts")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer");
    }

    @Test
    void devuelve401AlCrearEnLaRutaConBasePathCuandoNoHayCredenciales() {
        webTestClient.post().uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"Billetera\", \"type\": \"CASH\", \"currencyCode\": \"COP\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer");
    }

    @Test
    void elListadoConElBasicCompartidoEsUn401() {
        webTestClient.get().uri("/api/accounts")
                .headers(BasicMother.cabecera())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elAltaConElBasicCompartidoEsUn401() {
        webTestClient.post().uri("/api/accounts")
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"Billetera\", \"type\": \"CASH\", \"currencyCode\": \"COP\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elPatchSinCredencialesEsUn401ConElRetoDelJwt() {
        webTestClient.patch().uri("/api/accounts/20000000-0000-7000-8000-000000000001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"Bolsillo\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elPatchConElBasicCompartidoEsUn401() {
        webTestClient.patch().uri("/api/accounts/20000000-0000-7000-8000-000000000001")
                .headers(BasicMother.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"Bolsillo\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elBorradoSinCredencialesEsUn401ConElRetoDelJwt() {
        webTestClient.delete().uri("/api/accounts/20000000-0000-7000-8000-000000000001")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elBorradoConElBasicCompartidoEsUn401() {
        webTestClient.delete().uri("/api/accounts/20000000-0000-7000-8000-000000000001")
                .headers(BasicMother.cabecera())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void noExponeElEndpointFueraDelBasePath() {
        webTestClient.get().uri("/accounts")
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .exchange()
                .expectStatus().isNotFound();
    }
}
