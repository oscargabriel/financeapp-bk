package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.model.ServiceHealth;
import com.oscargabriel.financeapp.domain.model.SystemStatus;
import com.oscargabriel.financeapp.domain.port.in.CheckSystemStatusPort;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest(StatusController.class)
@Import(SecurityConfig.class)
class StatusControllerTest {

    // El slice monta el controller directamente: spring.webflux.base-path (/api) lo aplica el
    // HttpHandler de un servidor real, no el WebTestClient del slice. La ruta completa /api/status
    // se verifica en StatusIT.
    private static final String STATUS_URI = "/status";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CheckSystemStatusPort checkSystemStatus;

    @Test
    void devuelve401CuandoNoHayCredenciales() {
        webTestClient.get().uri(STATUS_URI)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void devuelve200ConTodosLosServiciosArriba() {
        when(checkSystemStatus.check()).thenReturn(
                Mono.just(SystemStatus.of(List.of(ServiceHealth.up("postgres")))));

        webTestClient.mutateWith(mockUser()).get().uri(STATUS_URI)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.services.postgres").isEqualTo("UP");
    }

    @Test
    void devuelve503IndicandoQueServicioEstaAbajo() {
        when(checkSystemStatus.check()).thenReturn(
                Mono.just(SystemStatus.of(List.of(
                        ServiceHealth.down("postgres"),
                        ServiceHealth.up("redis")))));

        webTestClient.mutateWith(mockUser()).get().uri(STATUS_URI)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo("DOWN")
                .jsonPath("$.services.postgres").isEqualTo("DOWN")
                .jsonPath("$.services.redis").isEqualTo("UP");
    }
}
