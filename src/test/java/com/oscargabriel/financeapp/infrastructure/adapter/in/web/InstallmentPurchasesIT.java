package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

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

import com.oscargabriel.financeapp.support.BasicMother;

/**
 * Servidor real: las cinco rutas de compras en cuotas (FA-108) quedan bajo spring.webflux.base-path y
 * protegidas por la cadena del JWT, que no acepta la credencial compartida del Basic. Con datos las verifica
 * bruno/installments/ contra PostgreSQL: el R2DBC de la suite apunta a un puerto sin escucha.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InstallmentPurchasesIT {

    private static final String COMPRA = "/api/installment-purchases/90000000-0000-7000-8000-000000000001";

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
    @CsvSource({"POST, /api/installment-purchases", "POST, /api/installment-purchases/preview",
            "GET, /api/installment-purchases", "PATCH, " + COMPRA, "DELETE, " + COMPRA})
    void sinCredencialEs401ConElRetoDelJwt(HttpMethod metodo, String ruta) {
        pedir(metodo, ruta, cabeceras -> { });
    }

    @ParameterizedTest(name = "{0} {1} con el Basic")
    @CsvSource({"POST, /api/installment-purchases", "POST, /api/installment-purchases/preview",
            "GET, /api/installment-purchases", "PATCH, " + COMPRA, "DELETE, " + COMPRA})
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
