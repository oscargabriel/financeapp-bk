package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.core.domain.model.NewRefreshToken;
import com.oscargabriel.financeapp.core.domain.model.RefreshToken;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.out.RefreshTokenGeneratorPort;
import com.oscargabriel.financeapp.core.domain.port.out.SesionRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TokenIssuerPort;

import reactor.core.publisher.Mono;

/**
 * Abre la sesion de los tres logins: contrasena, registro con Google y login con Google (design.md de
 * FIN-52, decision 8). Que sea una sola es lo que hace que sus tokens se refresquen, se revoquen y
 * venzan igual.
 */
@Component
public class AperturaDeSesion {

    private final TokenIssuerPort emisor;
    private final RefreshTokenGeneratorPort generador;
    private final SesionRepositoryPort sesiones;
    private final Clock clock;
    private final Duration duracionDeLaSesion;

    public AperturaDeSesion(
            TokenIssuerPort emisor,
            RefreshTokenGeneratorPort generador,
            SesionRepositoryPort sesiones,
            Clock clock,
            @Value("${spring.security.jwt.refresh-expiration}") Duration duracionDeLaSesion) {
        this.emisor = emisor;
        this.generador = generador;
        this.sesiones = sesiones;
        this.clock = clock;
        this.duracionDeLaSesion = duracionDeLaSesion;
    }

    /**
     * Abre una familia nueva de refresh tokens que vence a las {@code duracionDeLaSesion} de abrirla.
     * La base guarda el hash; el valor solo viaja en la respuesta.
     */
    public Mono<SessionTokens> abrir(UUID userId, long tokenVersion) {
        return Mono.defer(() -> {
            Instant ahora = clock.instant();
            String refresh = generador.generar();
            NewRefreshToken fila = new NewRefreshToken(
                    UuidV7.from(ahora),
                    userId,
                    UuidV7.from(ahora),
                    generador.hash(refresh),
                    tokenVersion,
                    ahora.plus(duracionDeLaSesion),
                    ahora);

            return sesiones.abrir(fila)
                    .then(Mono.fromSupplier(() -> new SessionTokens(
                            emisor.issueFor(userId, tokenVersion),
                            new RefreshToken(refresh, duracionDeLaSesion))));
        });
    }
}
