package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.port.in.LogoutPort;
import com.oscargabriel.financeapp.core.domain.port.out.RefreshTokenGeneratorPort;
import com.oscargabriel.financeapp.core.domain.port.out.SesionRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class LogoutUseCase implements LogoutPort {

    private final SesionRepositoryPort sesiones;
    private final RefreshTokenGeneratorPort generador;
    private final Clock clock;

    /**
     * Revoca la sesion entera, no solo ese refresh: es la misma cosa, porque en una sesion solo hay
     * un refresh vivo. El access sigue sirviendo hasta vencer; el cliente lo descarta.
     */
    @Override
    public Mono<Void> logout(String refreshToken) {
        return Mono.defer(() -> sesiones.revocarSesionDe(generador.hash(refreshToken), clock.instant()));
    }
}
