package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.Duration;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.support.BasicMother;

/**
 * Servidor real: las tres rutas del perfil quedan bajo spring.webflux.base-path y en la cadena del
 * JWT. El camino con datos lo verifica bruno/users/ contra PostgreSQL, porque el R2DBC de la suite
 * apunta a un puerto sin escucha.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsersIT {

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

    @ParameterizedTest
    @ValueSource(strings = {"GET /api/users/me", "PATCH /api/users/me", "PUT /api/users/me/password"})
    void sinCredencialesEsUn401ConElRetoDelJwt(String ruta) {
        pedir(ruta, cabeceras -> { })
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    /** El Basic compartido abre /auth/* y /status, no el perfil. */
    @ParameterizedTest
    @ValueSource(strings = {"GET /api/users/me", "PATCH /api/users/me", "PUT /api/users/me/password"})
    void conElBasicCompartidoEsUn401(String ruta) {
        pedir(ruta, BasicMother.cabecera())
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED");
    }

    private WebTestClient.ResponseSpec pedir(String ruta, Consumer<HttpHeaders> cabeceras) {
        String[] partes = ruta.split(" ");
        return webTestClient.method(HttpMethod.valueOf(partes[0])).uri(partes[1])
                .headers(cabeceras)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange();
    }
}
