package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.oscargabriel.financeapp.support.BasicMother;
import com.oscargabriel.financeapp.support.TokenMother;

/**
 * Las dos cadenas de seguridad enteras contra un servidor real, que es el unico sitio donde se
 * ejercitan junto al base-path /api: las reglas se escriben sin ese prefijo porque lo quita el
 * HttpHandler antes, y un slice web no lo montaria.
 *
 * Desde FA-43 lo que se prueba no es solo que cada credencial sirva en su cadena, sino que la otra
 * no sirva: el securityMatcher es lo unico que separa las dos, y un matcher mal escrito dejaria
 * cualquiera de las dos credenciales entrando por las dos puertas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtSecurityIT {

    private static final String RUTA_PROTEGIDA = "/api/eco-del-token";

    @Value("${local.server.port}")
    private int port;

    private WebTestClient webTestClient;

    /**
     * Ruta de usar y tirar que devuelve el subject del token. Es la forma de comprobar que el
     * userId llega al controlador sin adelantar FA-15, que es la que va a consumirlo de verdad.
     */
    @TestConfiguration
    static class RutaDePrueba {

        @Bean
        RouterFunction<ServerResponse> ecoDelToken() {
            return route(GET("/eco-del-token"), request -> ReactiveSecurityContextHolder.getContext()
                    .map(contexto -> (Jwt) contexto.getAuthentication().getPrincipal())
                    .flatMap(jwt -> ServerResponse.ok().bodyValue(jwt.getSubject())));
        }
    }

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    void dejaPasarConUnTokenValidoYEntregaElUsuarioAlControlador() {
        webTestClient.get().uri(RUTA_PROTEGIDA)
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(TokenMother.USUARIO.toString());
    }

    @Test
    void devuelve401SinTokenConElFormatoDeErrorDelProyecto() {
        webTestClient.get().uri(RUTA_PROTEGIDA)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].description").isEqualTo("Autenticacion requerida")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void devuelve401ConUnTokenExpirado() {
        esperaUn401Identico(TokenMother.expirado());
    }

    @Test
    void devuelve401ConUnTokenFirmadoPorOtro() {
        esperaUn401Identico(TokenMother.conOtraFirma());
    }

    /** Un token bien firmado pero de otra aplicacion que comparta la clave no entra aqui. */
    @Test
    void devuelve401ConUnTokenDeOtroEmisor() {
        esperaUn401Identico(TokenMother.deOtroEmisor());
    }

    @Test
    void devuelve401ConUnaCadenaQueNiSiquieraEsUnToken() {
        esperaUn401Identico("esto-no-es-un-jwt");
    }

    /**
     * OWASP A05. El entry point de Spring publica el motivo en WWW-Authenticate como
     * error_description ("Jwt expired at ...", "Signed JWT rejected..."), que le dice a quien
     * prueba tokens exactamente cual de sus intentos se acerco mas.
     */
    @Test
    void elRechazoNoCuentaPorQueFalloElToken() {
        String cabecera = webTestClient.get().uri(RUTA_PROTEGIDA)
                .headers(headers -> headers.setBearerAuth(TokenMother.expirado()))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists(HttpHeaders.WWW_AUTHENTICATE)
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst(HttpHeaders.WWW_AUTHENTICATE);

        assertThat(cabecera).isEqualTo("Bearer");
        assertThat(cabecera).doesNotContain("error_description", "expired", "invalid_token");
    }

    @Test
    void elCuerpoDelRechazoTampocoLoCuenta() {
        byte[] cuerpo = webTestClient.get().uri(RUTA_PROTEGIDA)
                .headers(headers -> headers.setBearerAuth(TokenMother.conOtraFirma()))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().returnResult().getResponseBody();

        assertThat(new String(cuerpo))
                .doesNotContain("signature", "Signed JWT", "expired", "Jwt", "iss");
    }

    /** Desde FA-43 el alta tampoco es publica: sin la credencial compartida no se puede pedir. */
    @Test
    void elLoginNoEsAlcanzableSinCredenciales() {
        webTestClient.post().uri("/api/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"email\":\"\",\"password\":\"\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void elRegistroNoEsAlcanzableSinCredenciales() {
        webTestClient.post().uri("/api/auth/register")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /**
     * Con el Basic llega al caso de uso y muere ahi por el payload vacio, no en el filtro: un 401
     * aqui querria decir que la credencial compartida no abre las rutas de auth.
     */
    @Test
    void elLoginEsAlcanzableConBasic() {
        webTestClient.post().uri("/api/auth/login")
                .headers(BasicMother.cabecera())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"email\":\"\",\"password\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void elRegistroEsAlcanzableConBasic() {
        webTestClient.post().uri("/api/auth/register")
                .headers(BasicMother.cabecera())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    /**
     * 503 y no 200 porque el R2DBC de la suite apunta a un puerto sin escucha: lo que este caso
     * prueba es que el Basic atraviesa la cadena, no el estado de la base. El 200 con la base
     * arriba solo lo ve bruno/system/status.yml, y el 200 con el puerto mockeado
     * StatusControllerTest.
     */
    @Test
    void elStatusEsAlcanzableConBasic() {
        webTestClient.get().uri("/api/status")
                .headers(BasicMother.cabecera())
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    /** El Bearer no sirve en la cadena del Basic, aunque el token sea impecable. */
    @Test
    void devuelve401EnElStatusConUnTokenValido() {
        webTestClient.get().uri("/api/status")
                .headers(headers -> headers.setBearerAuth(TokenMother.valido()))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED");
    }

    /** Y el Basic no sirve en la cadena del JWT. */
    @Test
    void devuelve401EnUnaRutaDeApiConBasicValido() {
        webTestClient.get().uri(RUTA_PROTEGIDA)
                .headers(BasicMother.cabecera())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void cualquierOtraRutaSigueCerrada() {
        webTestClient.get().uri("/api/status")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/no-existe")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /**
     * Cada cadena anuncia su propio esquema, y ninguna de las dos anuncia un realm: el nombre de
     * la aplicacion no tiene por que viajar en la cabecera de un rechazo.
     */
    @Test
    void cadaCadenaRetaConSuPropioEsquema() {
        assertThat(retoDe("/api/status")).isEqualTo("Basic");
        assertThat(retoDe(RUTA_PROTEGIDA)).isEqualTo("Bearer");

        String retoDelLogin = webTestClient.post().uri("/api/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized()
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst(HttpHeaders.WWW_AUTHENTICATE);

        assertThat(retoDelLogin).isEqualTo("Basic");
    }

    private String retoDe(String ruta) {
        return webTestClient.get().uri(ruta)
                .exchange()
                .expectStatus().isUnauthorized()
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst(HttpHeaders.WWW_AUTHENTICATE);
    }

    private void esperaUn401Identico(String token) {
        webTestClient.get().uri(RUTA_PROTEGIDA)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].description").isEqualTo("Autenticacion requerida");
    }
}
