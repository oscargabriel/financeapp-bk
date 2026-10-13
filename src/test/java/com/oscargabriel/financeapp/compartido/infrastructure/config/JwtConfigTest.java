package com.oscargabriel.financeapp.compartido.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import com.oscargabriel.financeapp.compartido.domain.port.out.SesionDelTokenPort;
import com.oscargabriel.financeapp.compartido.support.TokenMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    private final SesionDelTokenPort sesiones = mock(SesionDelTokenPort.class);

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
    void elDecoderAceptaUnTokenPropioConLaSesionAbierta() {
        when(sesiones.abierta(TokenMother.USUARIO, TokenMother.VERSION_DE_SESION))
                .thenReturn(Mono.just(true));

        StepVerifier.create(decoder().decode(TokenMother.valido()))
                .assertNext(jwt -> assertThat(jwt.getSubject())
                        .isEqualTo(TokenMother.USUARIO.toString()))
                .verifyComplete();
    }

    @Test
    void elDecoderRechazaUnTokenExpirado() {
        StepVerifier.create(decoder().decode(TokenMother.expirado())).verifyError();
        verifyNoInteractions(sesiones);
    }

    @Test
    void elDecoderRechazaUnTokenFirmadoPorOtro() {
        StepVerifier.create(decoder().decode(TokenMother.conOtraFirma())).verifyError();
        verifyNoInteractions(sesiones);
    }

    /** Sin validar el emisor, un token firmado con la misma clave por otro servicio entraria. */
    @Test
    void elDecoderRechazaUnTokenDeOtroEmisor() {
        StepVerifier.create(decoder().decode(TokenMother.deOtroEmisor())).verifyError();
        verifyNoInteractions(sesiones);
    }

    /**
     * Cambio de contrasena, usuario desactivado o borrado: el puerto no distingue y el decoder
     * tampoco. Tiene que ser BadJwtException, la unica que el resource server convierte en 401;
     * cualquier otra JwtException sale como 500.
     */
    @Test
    void elDecoderRechazaUnTokenDeUnaSesionCerrada() {
        when(sesiones.abierta(TokenMother.USUARIO, TokenMother.VERSION_DE_SESION))
                .thenReturn(Mono.just(false));

        StepVerifier.create(decoder().decode(TokenMother.valido()))
                .verifyError(BadJwtException.class);
    }

    @Test
    void elDecoderRechazaUnTokenSinVersionDeSesionSinConsultarLaBase() {
        StepVerifier.create(decoder().decode(TokenMother.sinVersionDeSesion()))
                .verifyError(BadJwtException.class);
        verifyNoInteractions(sesiones);
    }

    @Test
    void elDecoderRechazaUnSujetoQueNoEsUuidSinConsultarLaBase() {
        StepVerifier.create(decoder().decode(TokenMother.conSujetoQueNoEsUuid()))
                .verifyError(BadJwtException.class);
        verifyNoInteractions(sesiones);
    }

    private ReactiveJwtDecoder decoder() {
        SecretKey clave = config.jwtSigningKey(TokenMother.SECRETO);
        return config.jwtDecoder(clave, TokenMother.EMISOR, sesiones);
    }
}
