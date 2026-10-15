package com.oscargabriel.financeapp.core.infrastructure.adapter.out.google;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Verifica el ID token de Google Identity Services contra las llaves publicas de Google (design.md de
 * FIN-52, decisiones 1 y 2). El decoder guarda las llaves y solo las vuelve a pedir ante un kid
 * desconocido. La URL del JWKS sale de configuracion, nunca de la peticion. El log de un rechazo dice
 * el tipo de fallo, nunca el token.
 *
 * WebClient.builder() y no el bean, por lo mismo que GeminiAssistantAdapter.
 */
@Component
@ConditionalOnProperty(name = "google.proveedor", havingValue = "google")
@Slf4j
public class GoogleIdTokenVerifierAdapter implements GoogleIdTokenVerifierPort {

    /** Google emite con los dos valores. */
    private static final Set<String> EMISORES = Set.of("accounts.google.com", "https://accounts.google.com");

    private final ReactiveJwtDecoder decoder;
    private final Duration timeout;

    public GoogleIdTokenVerifierAdapter(
            @Value("${google.client-id}") String clientId,
            @Value("${google.jwks-uri}") String jwksUri,
            @Value("${google.timeout}") Duration timeout) {
        NimbusReactiveJwtDecoder nimbus = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri)
                .webClient(WebClient.builder().build())
                .build();
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<String>(JwtClaimNames.ISS, EMISORES::contains),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audiencia -> audiencia != null && audiencia.contains(clientId))));
        this.decoder = nimbus;
        this.timeout = timeout;
    }

    /**
     * El decoder separa los dos fallos: un token que no sirve sale como JwtException, y unas llaves
     * que no se pudieron obtener como IllegalStateException.
     */
    @Override
    public Mono<GoogleIdentity> verificar(String idToken) {
        return Mono.defer(() -> decoder.decode(idToken))
                .timeout(timeout)
                .filter(GoogleIdTokenVerifierAdapter::correoVerificado)
                .map(jwt -> GoogleIdentity.desdeClaims(jwt.getSubject(), jwt.getClaimAsString("email"),
                        jwt.getClaimAsString("given_name"), jwt.getClaimAsString("name"),
                        jwt.getClaimAsString("family_name"), jwt.getIssuedAt()))
                .onErrorResume(JwtException.class, e -> {
                    log.info("ID token de Google rechazado: {}", e.getClass().getSimpleName());
                    return Mono.empty();
                })
                .onErrorMap(e -> e instanceof IllegalStateException || e instanceof TimeoutException,
                        GoogleIdTokenVerifierAdapter::noDisponible);
    }

    /** email_verified puede llegar como booleano o como texto. Sin un correo con @ no hay usuario que crear. */
    private static boolean correoVerificado(Jwt jwt) {
        String correo = jwt.getClaimAsString("email");
        return Boolean.parseBoolean(jwt.getClaimAsString("email_verified"))
                && correo != null && correo.contains("@") && jwt.getSubject() != null;
    }

    private static BadRequestException noDisponible(Throwable e) {
        log.warn("No se pudieron obtener las llaves de Google: {}", e.getClass().getSimpleName());
        return new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR,
                "El servicio de Google no esta disponible", "idToken", e);
    }
}
