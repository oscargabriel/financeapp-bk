package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import java.time.Duration;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.compartido.support.BasicMother;

/**
 * Servidor real: las seis rutas de afiliaciones (FIN-65) y las del saldo y el detalle (FIN-67) quedan
 * bajo spring.webflux.base-path y protegidas por la cadena del JWT, que no acepta la credencial
 * compartida del Basic. Con datos las verifican bruno/affiliations/ y bruno/affiliate-balances/ contra
 * PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AffiliationsIT {

    private static final String RELACION = "/api/affiliations/90000000-0000-7000-8000-000000000001";

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

    @ParameterizedTest(name = "{0} {1} sin credencial")
    @CsvSource({"GET, /api/affiliations", "POST, /api/affiliations", "POST, " + RELACION + "/accept",
            "POST, " + RELACION + "/reject", "POST, " + RELACION + "/cancel", "POST, " + RELACION + "/unlink",
            "GET, /api/affiliations/balances", "GET, " + RELACION + "/shares"})
    void sinCredencialEs401ConElRetoDelJwt(HttpMethod metodo, String ruta) {
        pedir(metodo, ruta, cabeceras -> { });
    }

    @ParameterizedTest(name = "{0} {1} con el Basic")
    @CsvSource({"GET, /api/affiliations", "POST, /api/affiliations", "POST, " + RELACION + "/accept",
            "POST, " + RELACION + "/reject", "POST, " + RELACION + "/cancel", "POST, " + RELACION + "/unlink",
            "GET, /api/affiliations/balances", "GET, " + RELACION + "/shares"})
    void elBasicCompartidoEs401ConElRetoDelJwt(HttpMethod metodo, String ruta) {
        pedir(metodo, ruta, BasicMother.cabecera());
    }

    private void pedir(HttpMethod metodo, String ruta, Consumer<HttpHeaders> cabeceras) {
        webTestClient.method(metodo).uri(ruta)
                .headers(cabeceras)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }
}
