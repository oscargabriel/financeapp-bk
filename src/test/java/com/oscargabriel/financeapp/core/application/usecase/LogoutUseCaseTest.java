package com.oscargabriel.financeapp.core.application.usecase;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.core.domain.port.out.RefreshTokenGeneratorPort;
import com.oscargabriel.financeapp.core.domain.port.out.SesionRepositoryPort;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-10-12T15:00:00Z");

    @Mock
    private SesionRepositoryPort sesiones;

    @Mock
    private RefreshTokenGeneratorPort generador;

    private LogoutUseCase logout;

    @BeforeEach
    void setUp() {
        logout = new LogoutUseCase(sesiones, generador, Clock.fixed(AHORA, ZoneOffset.UTC));
        when(generador.hash("un-refresh")).thenReturn("hash-del-refresh");
    }

    /**
     * Revoca la sesion por el hash, que es lo unico que hay en la base. Que el refresh exista o no
     * no cambia la respuesta: el puerto completa vacio en los dos casos.
     */
    @Test
    void revocaLaSesionDelRefreshYCompletaVacio() {
        when(sesiones.revocarSesionDe("hash-del-refresh", AHORA)).thenReturn(Mono.empty());

        StepVerifier.create(logout.logout("un-refresh")).verifyComplete();

        verify(sesiones).revocarSesionDe("hash-del-refresh", AHORA);
    }
}
