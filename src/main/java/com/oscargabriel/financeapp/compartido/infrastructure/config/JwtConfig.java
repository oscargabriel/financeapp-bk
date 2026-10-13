package com.oscargabriel.financeapp.compartido.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import com.oscargabriel.financeapp.compartido.domain.port.out.SesionDelTokenPort;

import reactor.core.publisher.Mono;

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

    /** El claim con la version de sesion del usuario al emitir el access (FIN-47). */
    public static final String CLAIM_VERSION_DE_SESION = "sv";

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
     *
     * Despues consulta la sesion (FIN-47): firma, emisor y vencimiento no bastan para dejar de
     * aceptar un token tras un cambio de contrasena o una desactivacion. Es una consulta por request,
     * a proposito: una cache volveria a dejar pasar el token hasta que expirara la entrada.
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(SecretKey clave,
            @Value("${spring.security.jwt.issuer}") String emisor,
            SesionDelTokenPort sesiones) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(clave)
                .macAlgorithm(ALGORITMO)
                .build();

        OAuth2TokenValidator<Jwt> validador = JwtValidators.createDefaultWithValidators(
                new JwtClaimValidator<String>(JwtClaimNames.ISS, emisor::equals));
        decoder.setJwtValidator(validador);
        return token -> decoder.decode(token)
                .flatMap(jwt -> conSesionAbierta(jwt, sesiones));
    }

    /**
     * BadJwtException y no otra: el resource server solo convierte esa en el 401 del entry point, y
     * cualquier otra JwtException en un 500. El motivo va al log, nunca al cliente.
     */
    private static Mono<Jwt> conSesionAbierta(Jwt jwt, SesionDelTokenPort sesiones) {
        UUID usuario;
        try {
            usuario = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return Mono.error(new BadJwtException("El subject no es un usuario", e));
        }
        if (!(jwt.getClaims().get(CLAIM_VERSION_DE_SESION) instanceof Number version)) {
            return Mono.error(new BadJwtException("El token no trae la version de sesion"));
        }
        return sesiones.abierta(usuario, version.longValue())
                .filter(Boolean::booleanValue)
                .map(abierta -> jwt)
                .switchIfEmpty(Mono.error(() -> new BadJwtException("La sesion del token ya no esta abierta")));
    }
}
