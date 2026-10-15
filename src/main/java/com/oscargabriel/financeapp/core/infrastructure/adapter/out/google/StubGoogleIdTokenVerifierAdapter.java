package com.oscargabriel.financeapp.core.infrastructure.adapter.out.google;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;

import reactor.core.publisher.Mono;

/**
 * Los tokens de mentira con los que bruno/auth-google/ prueba el flujo sin Google (design.md de FIN-52,
 * decision 3): stub|sub|email|nombre|apellido, con nombre y apellido opcionales. Un sexto campo
 * opcional es el iat en segundos epoch; sin el, el token sale emitido ahora (FIN-53, decision 2).
 * Cualquier otro texto es un token invalido. Con el perfil prod no existe aunque se pida, y entonces no
 * hay verificador y no arranca.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "google.proveedor", havingValue = "stub")
@AllArgsConstructor
public class StubGoogleIdTokenVerifierAdapter implements GoogleIdTokenVerifierPort {

    private static final String PREFIJO = "stub";
    private static final int PARTES = 5;
    private static final int PARTES_CON_EMISION = 6;
    private static final Pattern SEGUNDOS = Pattern.compile("\\d{1,12}");

    private final Clock clock;

    @Override
    public Mono<GoogleIdentity> verificar(String idToken) {
        String[] partes = idToken.split("\\|", -1);
        if ((partes.length != PARTES && partes.length != PARTES_CON_EMISION) || !PREFIJO.equals(partes[0])
                || partes[1].isBlank() || !partes[2].contains("@")
                || (partes.length == PARTES_CON_EMISION && !SEGUNDOS.matcher(partes[5]).matches())) {
            return Mono.empty();
        }
        Instant emitidoEn = partes.length == PARTES_CON_EMISION
                ? Instant.ofEpochSecond(Long.parseLong(partes[5]))
                : clock.instant();
        return Mono.just(GoogleIdentity.desdeClaims(partes[1], partes[2], partes[3], null, partes[4], emitidoEn));
    }
}
