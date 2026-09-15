package com.oscargabriel.financeapp.infrastructure.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.oscargabriel.financeapp.domain.model.AccessToken;
import com.oscargabriel.financeapp.domain.port.out.TokenIssuerPort;

/**
 * Firma HMAC-SHA256 con un secreto compartido. Basta mientras el emisor y el validador son la misma
 * aplicacion; el dia que un tercero tenga que verificar tokens sin poder firmarlos, el cambio es a
 * un par de claves asimetrico y solo se toca esta clase.
 */
@Component
public class JwtTokenIssuerAdapter implements TokenIssuerPort {

    /** HS256 exige una clave de al menos 256 bits: con menos, Nimbus rechaza la firma. */
    private static final int BYTES_MINIMOS_SECRETO = 32;

    private static final MacAlgorithm ALGORITMO = MacAlgorithm.HS256;

    private final JwtEncoder encoder;
    private final Duration vigencia;
    private final String emisor;
    private final Clock clock;

    public JwtTokenIssuerAdapter(
            @Value("${spring.security.jwt.secret}") String secreto,
            @Value("${spring.security.jwt.expiration}") Duration vigencia,
            @Value("${spring.security.jwt.issuer}") String emisor,
            Clock clock) {
        byte[] clave = secreto.getBytes(StandardCharsets.UTF_8);
        if (clave.length < BYTES_MINIMOS_SECRETO) {
            throw new IllegalStateException("spring.security.jwt.secret debe tener al menos "
                    + BYTES_MINIMOS_SECRETO + " bytes para firmar con " + ALGORITMO.getName());
        }
        this.encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(clave, ALGORITMO.getName())));
        this.vigencia = vigencia;
        this.emisor = emisor;
        this.clock = clock;
    }

    @Override
    public AccessToken issueFor(UUID userId) {
        Instant ahora = clock.instant();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emisor)
                .subject(userId.toString())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(vigencia))
                .build();

        String token = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(ALGORITMO).build(), claims))
                .getTokenValue();

        return new AccessToken(token, vigencia);
    }
}
