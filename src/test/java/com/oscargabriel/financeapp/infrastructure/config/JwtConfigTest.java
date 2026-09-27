package com.oscargabriel.financeapp.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.oscargabriel.financeapp.support.TokenMother;

import reactor.test.StepVerifier;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    /**
     * HS256 no firma con menos de 256 bits. Fallar al construir el bean deja el contexto abajo en
     * el arranque, en vez de devolver 500 en el primer login de produccion.
     */
    @Test
    void noConstruyeLaClaveSiElSecretoEsDemasiadoCorto() {
        assertThatThrownBy(() -> config.jwtSigningKey("corto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.security.jwt.secret");
    }

    /** El mensaje se loguea al arrancar: no puede llevar el secreto dentro. */
    @Test
    void elMensajeDelSecretoCortoNoRepiteElSecreto() {
        assertThatThrownBy(() -> config.jwtSigningKey("clave-corta"))
                .hasMessageNotContaining("clave-corta");
    }

    @Test
    void aceptaUnSecretoDeExactamente32Bytes() {
        assertThatCode(() -> config.jwtSigningKey(TokenMother.SECRETO)).doesNotThrowAnyException();
        assertThat(config.jwtSigningKey(TokenMother.SECRETO).getAlgorithm()).isEqualTo("HS256");
    }

    @Test
    void elDecoderAceptaUnTokenPropio() {
        StepVerifier.create(decoder().decode(TokenMother.valido()))
                .assertNext(jwt -> assertThat(jwt.getSubject())
                        .isEqualTo(TokenMother.USUARIO.toString()))
                .verifyComplete();
    }

    @Test
    void elDecoderRechazaUnTokenExpirado() {
        StepVerifier.create(decoder().decode(TokenMother.expirado())).verifyError();
    }

    @Test
    void elDecoderRechazaUnTokenFirmadoPorOtro() {
        StepVerifier.create(decoder().decode(TokenMother.conOtraFirma())).verifyError();
    }

    /** Sin validar el emisor, un token firmado con la misma clave por otro servicio entraria. */
    @Test
    void elDecoderRechazaUnTokenDeOtroEmisor() {
        StepVerifier.create(decoder().decode(TokenMother.deOtroEmisor())).verifyError();
    }

    private org.springframework.security.oauth2.jwt.ReactiveJwtDecoder decoder() {
        SecretKey clave = config.jwtSigningKey(TokenMother.SECRETO);
        return config.jwtDecoder(clave, TokenMother.EMISOR);
    }
}
