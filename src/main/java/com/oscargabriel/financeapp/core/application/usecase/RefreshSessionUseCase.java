package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.RefreshToken;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.RefreshSessionPort;
import com.oscargabriel.financeapp.core.domain.port.out.RefreshTokenGeneratorPort;
import com.oscargabriel.financeapp.core.domain.port.out.SesionRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TokenIssuerPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class RefreshSessionUseCase implements RefreshSessionPort {

    private final SesionRepositoryPort sesiones;
    private final RefreshTokenGeneratorPort generador;
    private final TokenIssuerPort emisor;
    private final Clock clock;

    /**
     * Cada refresh sirve una vez. Si no se pudo rotar, se revoca su sesion entera antes de responder:
     * cuando el motivo es un reuso, eso deja sin servir el reemplazo que tiene quien lo uso primero,
     * y el legitimo y el atacante vuelven los dos al login.
     */
    @Override
    public Mono<SessionTokens> refresh(String refreshToken) {
        return Mono.defer(() -> {
            Instant ahora = clock.instant();
            String hashUsado = generador.hash(refreshToken);
            String nuevo = generador.generar();

            return sesiones.rotar(hashUsado, UuidV7.from(ahora), generador.hash(nuevo), ahora)
                    .map(sesion -> new SessionTokens(
                            emisor.issueFor(sesion.userId(), sesion.tokenVersion()),
                            new RefreshToken(nuevo, Duration.between(ahora, sesion.expiresAt()))))
                    .switchIfEmpty(Mono.defer(() -> sesiones.revocarSesionDe(hashUsado, ahora))
                            .then(Mono.error(RefreshSessionUseCase::refreshInvalido)));
        });
    }

    /**
     * El mismo 401 y el mismo texto que el resto de la API, en el campo del refresh: el cliente solo
     * tiene que saber que vuelve al login, no cual de los motivos fue.
     */
    private static BadRequestException refreshInvalido() {
        return new BadRequestException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED,
                "Autenticacion requerida", "refreshToken");
    }
}
