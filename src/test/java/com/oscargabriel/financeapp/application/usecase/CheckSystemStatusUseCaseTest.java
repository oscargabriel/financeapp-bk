package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.domain.model.HealthStatus;
import com.oscargabriel.financeapp.domain.model.ServiceHealth;
import com.oscargabriel.financeapp.domain.port.out.HealthCheckPort;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CheckSystemStatusUseCaseTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Mock
    private HealthCheckPort postgres;

    @Mock
    private HealthCheckPort redis;

    @Test
    void reportaTodosArribaCuandoTodosLosPuertosResponden() {
        when(postgres.serviceName()).thenReturn("postgres");
        when(postgres.ping()).thenReturn(Mono.empty());
        when(redis.serviceName()).thenReturn("redis");
        when(redis.ping()).thenReturn(Mono.empty());

        StepVerifier.create(new CheckSystemStatusUseCase(List.of(postgres, redis), TIMEOUT).check())
                .assertNext(estado -> {
                    assertThat(estado.status()).isEqualTo(HealthStatus.UP);
                    assertThat(estado.services())
                            .extracting(ServiceHealth::name, ServiceHealth::status)
                            .containsExactly(
                                    tuple("postgres", HealthStatus.UP),
                                    tuple("redis", HealthStatus.UP));
                })
                .verifyComplete();
    }

    @Test
    void marcaAbajoSoloElServicioQueFallaYConservaLosDemas() {
        when(postgres.serviceName()).thenReturn("postgres");
        when(postgres.ping()).thenReturn(Mono.error(new IllegalStateException("conexion rechazada")));
        when(redis.serviceName()).thenReturn("redis");
        when(redis.ping()).thenReturn(Mono.empty());

        StepVerifier.create(new CheckSystemStatusUseCase(List.of(postgres, redis), TIMEOUT).check())
                .assertNext(estado -> {
                    assertThat(estado.status()).isEqualTo(HealthStatus.DOWN);
                    assertThat(estado.services())
                            .extracting(ServiceHealth::name, ServiceHealth::status)
                            .containsExactly(
                                    tuple("postgres", HealthStatus.DOWN),
                                    tuple("redis", HealthStatus.UP));
                })
                .verifyComplete();
    }

    @Test
    void marcaAbajoElServicioQueNoRespondeDentroDelTimeout() {
        when(postgres.serviceName()).thenReturn("postgres");
        when(postgres.ping()).thenReturn(Mono.never());

        StepVerifier.withVirtualTime(
                        () -> new CheckSystemStatusUseCase(List.of(postgres), TIMEOUT).check())
                .expectSubscription()
                .expectNoEvent(TIMEOUT)
                .assertNext(estado -> {
                    assertThat(estado.status()).isEqualTo(HealthStatus.DOWN);
                    assertThat(estado.services()).containsExactly(ServiceHealth.down("postgres"));
                })
                .verifyComplete();
    }

    @Test
    void reportaArribaSinServiciosCuandoNoHayPuertosRegistrados() {
        StepVerifier.create(new CheckSystemStatusUseCase(List.of(), TIMEOUT).check())
                .assertNext(estado -> {
                    assertThat(estado.status()).isEqualTo(HealthStatus.UP);
                    assertThat(estado.services()).isEmpty();
                })
                .verifyComplete();
    }
}
