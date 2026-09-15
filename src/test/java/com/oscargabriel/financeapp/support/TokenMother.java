package com.oscargabriel.financeapp.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Tokens para los tests de la cadena de seguridad. Firma con el mismo secreto y el mismo emisor
 * que declara src/test/resources/application.yaml, asi que un token de aqui es indistinguible de
 * uno que emita la aplicacion en la suite.
 */
public final class TokenMother {

    public static final String SECRETO = "secreto-de-pruebas-de-32-bytes!!";

    public static final String EMISOR = "financeapp-bk-test";

    public static final UUID USUARIO = UUID.fromString("10000000-0000-7000-8000-000000000001");

    private TokenMother() {
    }

    public static String valido() {
        return firmado(SECRETO, EMISOR, Instant.now().plus(1, ChronoUnit.HOURS));
    }

    public static String expirado() {
        return firmado(SECRETO, EMISOR, Instant.now().minus(1, ChronoUnit.MINUTES));
    }

    /** Bien formado y sin expirar, pero firmado por quien no tiene la clave de esta aplicacion. */
    public static String conOtraFirma() {
        return firmado("otro-secreto-igual-de-largo!!!!!", EMISOR,
                Instant.now().plus(1, ChronoUnit.HOURS));
    }

    /** Firma valida y emisor ajeno: el caso de otro servicio que comparte la clave por error. */
    public static String deOtroEmisor() {
        return firmado(SECRETO, "otra-aplicacion", Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private static String firmado(String secreto, String emisor, Instant expiracion) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(emisor)
                .subject(USUARIO.toString())
                .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(expiracion))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(new SecretKeySpec(secreto.getBytes(), JWSAlgorithm.HS256.getName())));
        } catch (JOSEException e) {
            throw new IllegalStateException("no se pudo firmar el token de prueba", e);
        }
        return jwt.serialize();
    }
}
