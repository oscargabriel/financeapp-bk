package com.oscargabriel.financeapp.core.infrastructure.adapter.out.google;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;
import com.sun.net.httpserver.HttpServer;

import reactor.test.StepVerifier;

/**
 * Contra un servidor de llaves propio que sirve un JWKS generado aqui: firma, emisor, audiencia,
 * vencimiento y email_verified se prueban con tokens reales, sin salir a Google.
 */
class GoogleIdTokenVerifierAdapterTest {

    private static final String CLIENT_ID = "cliente-de-pruebas.apps.googleusercontent.com";
    private static final String EMISOR = "https://accounts.google.com";

    private static RSAKey llave;
    private static RSAKey otraLlave;

    private HttpServer servidor;
    private volatile int status = 200;
    private volatile long demoraMs = 0;

    @BeforeAll
    static void generarLlaves() throws Exception {
        llave = new RSAKeyGenerator(2048).keyID("llave-1").generate();
        otraLlave = new RSAKeyGenerator(2048).keyID("llave-1").generate();
    }

    @BeforeEach
    void levantarGoogleFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/oauth2/v3/certs", intercambio -> {
            try {
                Thread.sleep(demoraMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = new JWKSet(llave.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(status, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    @Test
    void devuelveLaIdentidadDeUnTokenValido() {
        StepVerifier.create(adapter().verificar(token(c -> {})))
                .expectNext(new GoogleIdentity("sub-123", "ana@gmail.com", "Ana", "Gomez"))
                .verifyComplete();
    }

    @Test
    void aceptaElEmisorSinEsquema() {
        StepVerifier.create(adapter().verificar(token(c -> c.issuer("accounts.google.com"))))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void aceptaEmailVerifiedComoTexto() {
        StepVerifier.create(adapter().verificar(token(c -> c.claim("email_verified", "true"))))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void pasaLosClaimsDeNombreAlModelo() {
        String sinNombreDePila = token(c -> c.claim("given_name", null).claim("family_name", null));

        StepVerifier.create(adapter().verificar(sinNombreDePila))
                .expectNext(new GoogleIdentity("sub-123", "ana@gmail.com", "Ana Gomez", null))
                .verifyComplete();
    }

    @Test
    void rechazaUnTokenFirmadoConOtraLlave() {
        StepVerifier.create(adapter().verificar(firmar(otraLlave, claims(c -> {}))))
                .verifyComplete();
    }

    @Test
    void rechazaOtroEmisor() {
        StepVerifier.create(adapter().verificar(token(c -> c.issuer("https://otro.example.com"))))
                .verifyComplete();
    }

    @Test
    void rechazaUnTokenParaOtroClientId() {
        StepVerifier.create(adapter().verificar(token(c -> c.audience("otro-cliente.apps.googleusercontent.com"))))
                .verifyComplete();
    }

    @Test
    void rechazaUnTokenVencido() {
        Instant haceUnaHora = Instant.now().minus(Duration.ofHours(1));

        StepVerifier.create(adapter().verificar(token(c -> c.issueTime(Date.from(haceUnaHora.minusSeconds(600)))
                        .expirationTime(Date.from(haceUnaHora)))))
                .verifyComplete();
    }

    @Test
    void rechazaUnCorreoSinVerificar() {
        StepVerifier.create(adapter().verificar(token(c -> c.claim("email_verified", false))))
                .verifyComplete();
    }

    @Test
    void rechazaUnCorreoSinVerificarComoTexto() {
        StepVerifier.create(adapter().verificar(token(c -> c.claim("email_verified", "false"))))
                .verifyComplete();
    }

    @Test
    void rechazaUnTokenSinCorreo() {
        StepVerifier.create(adapter().verificar(token(c -> c.claim("email", null))))
                .verifyComplete();
    }

    @Test
    void rechazaUnTextoQueNoEsUnJwt() {
        StepVerifier.create(adapter().verificar("esto-no-es-un-id-token"))
                .verifyComplete();
    }

    @Test
    void responde502SiLasLlavesNoSePuedenObtener() {
        status = 500;

        StepVerifier.create(adapter().verificar(token(c -> {})))
                .verifyErrorSatisfies(GoogleIdTokenVerifierAdapterTest::esGoogleNoDisponible);
    }

    @Test
    void responde502SiGoogleNoResponde() {
        servidor.stop(0);

        StepVerifier.create(adapter().verificar(token(c -> {})))
                .verifyErrorSatisfies(GoogleIdTokenVerifierAdapterTest::esGoogleNoDisponible);
    }

    @Test
    void responde502SiGoogleTardaMasQueElTimeout() {
        demoraMs = 1_000;

        StepVerifier.create(adapterCon(Duration.ofMillis(200)).verificar(token(c -> {})))
                .verifyErrorSatisfies(GoogleIdTokenVerifierAdapterTest::esGoogleNoDisponible);
    }

    /** Holgado: el primer request de la clase paga el arranque en frio de Netty. */
    private GoogleIdTokenVerifierAdapter adapter() {
        return adapterCon(Duration.ofSeconds(5));
    }

    private GoogleIdTokenVerifierAdapter adapterCon(Duration timeout) {
        return new GoogleIdTokenVerifierAdapter(CLIENT_ID,
                "http://localhost:" + servidor.getAddress().getPort() + "/oauth2/v3/certs", timeout);
    }

    private static String token(Consumer<JWTClaimsSet.Builder> ajuste) {
        return firmar(llave, claims(ajuste));
    }

    /** Los claims de un ID token de Google Identity Services, todos validos. */
    private static JWTClaimsSet claims(Consumer<JWTClaimsSet.Builder> ajuste) {
        Instant ahora = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .audience(CLIENT_ID)
                .subject("sub-123")
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plus(Duration.ofHours(1))))
                .claim("email", "Ana@Gmail.com")
                .claim("email_verified", true)
                .claim("name", "Ana Gomez")
                .claim("given_name", "Ana")
                .claim("family_name", "Gomez");
        ajuste.accept(claims);
        return claims.build();
    }

    private static String firmar(RSAKey conLlave, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(conLlave.getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build(), claims);
            jwt.sign(new RSASSASigner(conLlave));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void esGoogleNoDisponible(Throwable error) {
        assertThat(error).isInstanceOf(BadRequestException.class);
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(bre.getErrorResponse().getErrors().get(0).getCode())
                .isEqualTo(ErrorCodes.EXTERNAL_SERVICE_ERROR.getCode());
        assertThat(bre.getErrorResponse().getErrors().get(0).getField()).isEqualTo("idToken");
    }
}
