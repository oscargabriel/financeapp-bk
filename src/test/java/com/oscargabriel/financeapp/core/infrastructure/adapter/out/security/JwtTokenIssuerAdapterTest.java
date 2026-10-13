package com.oscargabriel.financeapp.core.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.oscargabriel.financeapp.compartido.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.core.domain.model.AccessToken;

class JwtTokenIssuerAdapterTest {

    private static final String SECRETO = "secreto-de-pruebas-de-32-bytes!!";

    private static final Instant AHORA = Instant.parse("2026-09-15T15:00:00Z");

    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneId.of("America/Bogota"));

    private static final Duration VIGENCIA = Duration.ofHours(1);

    private static final UUID ID = UUID.fromString("01994f00-0000-7000-8000-000000000001");

    private static final long VERSION = 3L;

    @Test
    void firmaElTokenConElSecretoConfigurado() throws ParseException, JOSEException {
        SignedJWT jwt = SignedJWT.parse(emitir().value());

        JWSVerifier verificador = new MACVerifier(
                new SecretKeySpec(SECRETO.getBytes(), JWSAlgorithm.HS256.getName()));

        assertThat(jwt.verify(verificador)).isTrue();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
    }

    /** Otro secreto no puede validar la firma: es lo que impide que un token se fabrique fuera. */
    @Test
    void unSecretoDistintoNoValidaLaFirma() throws ParseException, JOSEException {
        SignedJWT jwt = SignedJWT.parse(emitir().value());

        JWSVerifier impostor = new MACVerifier(new SecretKeySpec(
                "otro-secreto-igual-de-largo!!!!!".getBytes(), JWSAlgorithm.HS256.getName()));

        assertThat(jwt.verify(impostor)).isFalse();
    }

    @Test
    void llevaElUsuarioComoSubject() throws ParseException {
        assertThat(SignedJWT.parse(emitir().value()).getJWTClaimsSet().getSubject())
                .isEqualTo(ID.toString());
    }

    @Test
    void expiraUnaVigenciaDespuesDeSerEmitido() throws ParseException {
        var claims = SignedJWT.parse(emitir().value()).getJWTClaimsSet();

        assertThat(claims.getIssueTime().toInstant()).isEqualTo(AHORA);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(AHORA.plus(VIGENCIA));
        assertThat(claims.getIssuer()).isEqualTo("financeapp-bk-test");
    }

    @Test
    void informaLaVigenciaJuntoAlToken() {
        assertThat(emitir().expiresIn()).isEqualTo(VIGENCIA);
    }

    /** El decoder compara este claim con la version del usuario en cada request (FIN-47). */
    @Test
    void llevaLaVersionDeSesionDelUsuario() throws ParseException {
        var claims = SignedJWT.parse(emitir().value()).getJWTClaimsSet();

        assertThat(claims.getLongClaim(JwtConfig.CLAIM_VERSION_DE_SESION)).isEqualTo(VERSION);
    }

    /**
     * Con el mismo usuario, la misma version y el mismo segundo, sin jti los dos tokens saldrian
     * identicos: un refresco inmediato devolveria el mismo access que ya tenia el cliente.
     */
    @Test
    void dosTokensDelMismoInstanteSonDistintos() throws ParseException {
        AccessToken uno = emitir();
        AccessToken otro = emitir();

        assertThat(uno.value()).isNotEqualTo(otro.value());
        assertThat(SignedJWT.parse(uno.value()).getJWTClaimsSet().getJWTID()).isNotBlank();
    }

    private static AccessToken emitir() {
        return new JwtTokenIssuerAdapter(
                new SecretKeySpec(SECRETO.getBytes(), JWSAlgorithm.HS256.getName()),
                VIGENCIA, "financeapp-bk-test", RELOJ)
                .issueFor(ID, VERSION);
    }
}
