package com.oscargabriel.financeapp.infrastructure.config.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.domain.port.out.HealthCheckPort;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DatabaseStartupCheckTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration BACKOFF = Duration.ofMillis(1);
    private static final Duration TIMEOUT = Duration.ofMillis(50);

    @Mock
    private HealthCheckPort postgres;

    private DatabaseStartupCheck check() {
        return new DatabaseStartupCheck(postgres, MAX_ATTEMPTS, BACKOFF, TIMEOUT);
    }

    @Test
    void dejaArrancarCuandoLaBaseRespondeAlPrimerIntento() {
        when(postgres.ping()).thenReturn(Mono.empty());

        assertThatCode(() -> check().afterSingletonsInstantiated()).doesNotThrowAnyException();

        verify(postgres, times(1)).ping();
    }

    @Test
    void dejaArrancarCuandoLaBaseRespondeEnUnReintento() {
        when(postgres.ping()).thenReturn(
                Mono.error(new IllegalStateException("conexion rechazada")),
                Mono.empty());

        assertThatCode(() -> check().afterSingletonsInstantiated()).doesNotThrowAnyException();

        verify(postgres, times(2)).ping();
    }

    @Test
    void abortaElArranqueCuandoLaBaseFallaEnTodosLosIntentos() {
        when(postgres.ping()).thenReturn(Mono.error(new IllegalStateException("conexion rechazada")));
        when(postgres.serviceName()).thenReturn("postgres");

        assertThatThrownBy(() -> check().afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("postgres");

        verify(postgres, times(MAX_ATTEMPTS)).ping();
    }

    @Test
    void abortaElArranqueCuandoLaBaseNoRespondeDentroDelTimeout() {
        when(postgres.ping()).thenReturn(Mono.never());
        when(postgres.serviceName()).thenReturn("postgres");

        assertThatThrownBy(() -> check().afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class);

        verify(postgres, times(MAX_ATTEMPTS)).ping();
    }

    @Test
    void noFiltraElDetalleTecnicoDeLaCausaEnElMensajeDeError() {
        when(postgres.ping()).thenReturn(
                Mono.error(new IllegalStateException("password authentication failed for user admin")));
        when(postgres.serviceName()).thenReturn("postgres");

        assertThatThrownBy(() -> check().afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("password"));
    }
}
