package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.compartido.support.BasicMother;
import com.oscargabriel.financeapp.compartido.support.SesionSiempreAbierta;

/**
 * Servidor real: la ruta queda bajo el base-path y en la cadena del JWT. El 200 con datos lo verifica
 * bruno/home/, porque el R2DBC de la suite apunta a un puerto sin escucha a proposito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SesionSiempreAbierta.class)
class HomeSummaryIT {

    private static final String RUTA = "/api/reports/home";

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
    void sinCredencialesEsUn401ConElRetoDelJwt() {
        webTestClient.get().uri(RUTA)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void elBasicCompartidoEsUn401() {
        webTestClient.get().uri(RUTA)
                .headers(BasicMother.cabecera())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }
}
