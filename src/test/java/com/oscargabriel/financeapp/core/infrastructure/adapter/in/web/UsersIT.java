package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.support.BasicMother;
import com.oscargabriel.financeapp.compartido.support.SesionSiempreAbierta;
import com.oscargabriel.financeapp.compartido.support.TokenMother;
import com.oscargabriel.financeapp.core.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.core.domain.port.in.UpdateUserProfilePort;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Mono;

/**
 * Servidor real: las tres rutas del perfil quedan bajo spring.webflux.base-path y en la cadena del
 * JWT. El camino con datos lo verifica bruno/users/ contra PostgreSQL, porque el R2DBC de la suite
 * apunta a un puerto sin escucha: por eso el caso de uso del parche es un mock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SesionSiempreAbierta.class)
class UsersIT {

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private UpdateUserProfilePort updateProfile;

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

    @Test
    void laMonedaSolaLlegaAlCasoDeUsoTalComoVino() {
        when(updateProfile.update(any(), any())).thenReturn(Mono.just(UserMother.unPerfil()));

        parcheDeMoneda()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.baseCurrencyCode").isEqualTo("COP");

        verify(updateProfile).update(TokenMother.USUARIO,
                new UpdateUserProfileCommand(null, null, null, null, null, " eur ", null));
    }

    /** El 502 del caso de uso pasa por el WebExceptionHandler con la forma de error del proyecto. */
    @Test
    void laMonedaSinTasaSaleComo502EnServer() {
        when(updateProfile.update(any(), any())).thenReturn(Mono.error(new BadRequestException(
                HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR,
                "No hay tasa de cambio para la moneda nueva; intenta de nuevo", "server")));

        parcheDeMoneda()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo("EXTERNAL_SERVICE_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("server");
    }

    private WebTestClient.ResponseSpec parcheDeMoneda() {
        return webTestClient.patch().uri("/api/users/me")
                .headers(cabeceras -> cabeceras.setBearerAuth(TokenMother.valido()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"baseCurrencyCode\": \" eur \"}")
                .exchange();
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
