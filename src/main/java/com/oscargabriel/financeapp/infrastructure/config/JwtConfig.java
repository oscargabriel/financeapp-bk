package com.oscargabriel.financeapp.infrastructure.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * La clave de firma vive aqui y en ningun otro sitio: el que emite los tokens y el que los valida
 * tienen que compartirla, y leerla dos veces por separado abriria la puerta a que una de las dos
 * lecturas cambie sola y el API deje de aceptar sus propios tokens.
 */
@Configuration
public class JwtConfig {

    /** HS256 exige una clave de al menos 256 bits: con menos, Nimbus rechaza firmar. */
    private static final int BYTES_MINIMOS_SECRETO = 32;

    public static final MacAlgorithm ALGORITMO = MacAlgorithm.HS256;

    /**
     * Fallar al construir el bean deja el contexto abajo en el arranque. Sin esta comprobacion, un
     * secreto corto pasaria el despliegue y reventaria como 500 en el primer login.
     */
    @Bean
    public SecretKey jwtSigningKey(@Value("${spring.security.jwt.secret}") String secreto) {
        byte[] clave = secreto.getBytes(StandardCharsets.UTF_8);
        if (clave.length < BYTES_MINIMOS_SECRETO) {
            throw new IllegalStateException("spring.security.jwt.secret debe tener al menos "
                    + BYTES_MINIMOS_SECRETO + " bytes para firmar con " + ALGORITMO.getName());
        }
        return new SecretKeySpec(clave, ALGORITMO.getName());
    }

    /**
     * Ademas de la firma valida la expiracion y el emisor. Sin lo segundo, un token firmado con la
     * misma clave por otro servicio del mismo despliegue entraria aqui como propio.
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(SecretKey clave,
            @Value("${spring.security.jwt.issuer}") String emisor) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(clave)
                .macAlgorithm(ALGORITMO)
                .build();

        OAuth2TokenValidator<Jwt> validador = JwtValidators.createDefaultWithValidators(
                new JwtClaimValidator<String>(JwtClaimNames.ISS, emisor::equals));
        decoder.setJwtValidator(validador);
        return decoder;
    }
}
