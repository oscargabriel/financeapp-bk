package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.AccessToken;
import com.oscargabriel.financeapp.core.domain.model.RefreshToken;
import com.oscargabriel.financeapp.core.domain.model.RotatedSession;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.port.out.RefreshTokenGeneratorPort;
import com.oscargabriel.financeapp.core.domain.port.out.SesionRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TokenIssuerPort;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshSessionUseCaseTest {

    private static final UUID USUARIO = UUID.fromString("01994f00-0000-7000-8000-000000000001");

    private static final long VERSION = 4L;

    private static final Instant AHORA = Instant.parse("2026-10-12T15:00:00Z");

    /** La sesion se abrio hace 4 horas: le quedan 20. */
    private static final Instant FIN_DE_LA_SESION = AHORA.plus(Duration.ofHours(20));

    private static final AccessToken TOKEN = new AccessToken("un.jwt.nuevo", Duration.ofHours(1));

    @Mock
    private SesionRepositoryPort sesiones;

    @Mock
    private RefreshTokenGeneratorPort generador;

    @Mock
    private TokenIssuerPort emisor;

    private RefreshSessionUseCase refresco;

    @BeforeEach
    void setUp() {
        refresco = new RefreshSessionUseCase(sesiones, generador, emisor, Clock.fixed(AHORA, ZoneOffset.UTC));
        when(generador.hash("refresh-viejo")).thenReturn("hash-viejo");
        when(generador.generar()).thenReturn("refresh-nuevo");
        when(generador.hash("refresh-nuevo")).thenReturn("hash-nuevo");
        when(emisor.issueFor(USUARIO, VERSION)).thenReturn(TOKEN);
        when(sesiones.revocarSesionDe(anyString(), any())).thenReturn(Mono.empty());
    }

    @Test
    void cambiaUnRefreshVigentePorUnAccessYUnRefreshNuevos() {
        when(sesiones.rotar(eq("hash-viejo"), any(), eq("hash-nuevo"), eq(AHORA)))
                .thenReturn(Mono.just(new RotatedSession(USUARIO, VERSION, FIN_DE_LA_SESION)));

        StepVerifier.create(refresco.refresh("refresh-viejo"))
                .expectNext(new SessionTokens(TOKEN, new RefreshToken("refresh-nuevo", Duration.ofHours(20))))
                .verifyComplete();
    }

    /** El reemplazo vence con la sesion, no 24 horas despues del refresco. */
    @Test
    void elRefreshNuevoConservaElVencimientoDeLaSesion() {
        when(sesiones.rotar(any(), any(), any(), any()))
                .thenReturn(Mono.just(new RotatedSession(USUARIO, VERSION, FIN_DE_LA_SESION)));

        StepVerifier.create(refresco.refresh("refresh-viejo"))
                .assertNext(tokens -> assertThat(tokens.refresh().expiresIn())
                        .isEqualTo(Duration.between(AHORA, FIN_DE_LA_SESION)))
                .verifyComplete();
        verify(sesiones, never()).revocarSesionDe(anyString(), any());
    }

    /**
     * Vencido, inexistente, usado, revocado o de un usuario que ya no puede entrar: la base no los
     * distingue y el caso de uso tampoco. Si era un reuso, revocar la sesion invalida el reemplazo;
     * en los demas casos no queda nada vivo que revocar.
     */
    @Test
    void unRefreshQueNoSirveRevocaSuSesionYDa401() {
        when(sesiones.rotar(any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(refresco.refresh("refresh-viejo"))
                .verifyErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BadRequestException.class);
                    BadRequestException rechazo = (BadRequestException) error;
                    ErrorDetail detalle = rechazo.getErrorResponse().getErrors().get(0);
                    assertThat(rechazo.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(detalle.getCode()).isEqualTo(ErrorCodes.UNAUTHENTICATED.getCode());
                    assertThat(detalle.getField()).isEqualTo("refreshToken");
                    assertThat(detalle.getDescription()).isEqualTo("Autenticacion requerida");
                });

        verify(sesiones).revocarSesionDe("hash-viejo", AHORA);
        verifyNoInteractions(emisor);
    }
}
