package com.oscargabriel.financeapp.infrastructure.adapter.out.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.oscargabriel.financeapp.domain.model.AccessToken;
import com.oscargabriel.financeapp.domain.port.out.TokenIssuerPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;

/**
 * Firma HMAC-SHA256 con la clave que declara {@link JwtConfig}, la misma con la que el resource
 * server valida. Basta mientras el emisor y el validador son la misma aplicacion; el dia que un
 * tercero tenga que verificar tokens sin poder firmarlos, el cambio es a un par asimetrico.
 */
@Component
public class JwtTokenIssuerAdapter implements TokenIssuerPort {

    private final JwtEncoder encoder;
    private final Duration vigencia;
    private final String emisor;
    private final Clock clock;

    public JwtTokenIssuerAdapter(
            SecretKey clave,
            @Value("${spring.security.jwt.expiration}") Duration vigencia,
            @Value("${spring.security.jwt.issuer}") String emisor,
            Clock clock) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
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
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(JwtConfig.ALGORITMO).build(), claims))
                .getTokenValue();

        return new AccessToken(token, vigencia);
    }
}
