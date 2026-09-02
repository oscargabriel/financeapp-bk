package com.oscargabriel.financeapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;

class SystemStatusTest {

    @Test
    void estaArribaCuandoTodosLosServiciosEstanArriba() {
        List<ServiceHealth> servicios = List.of(ServiceHealth.up("postgres"), ServiceHealth.up("redis"));

        SystemStatus resultado = SystemStatus.of(servicios);

        assertThat(resultado.status()).isEqualTo(HealthStatus.UP);
        assertThat(resultado.services()).containsExactlyElementsOf(servicios);
    }

    @Test
    void estaAbajoCuandoAlMenosUnServicioEstaAbajo() {
        List<ServiceHealth> servicios = List.of(ServiceHealth.up("postgres"), ServiceHealth.down("redis"));

        SystemStatus resultado = SystemStatus.of(servicios);

        assertThat(resultado.status()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void conservaTodosLosServiciosCuandoAlgunoEstaAbajo() {
        SystemStatus resultado = SystemStatus.of(
                List.of(ServiceHealth.up("postgres"), ServiceHealth.down("redis")));

        assertThat(resultado.services())
                .extracting(ServiceHealth::name, ServiceHealth::status)
                .containsExactly(
                        tuple("postgres", HealthStatus.UP),
                        tuple("redis", HealthStatus.DOWN));
    }

    @Test
    void estaArribaCuandoNoHayServiciosRegistrados() {
        SystemStatus resultado = SystemStatus.of(List.of());

        assertThat(resultado.status()).isEqualTo(HealthStatus.UP);
        assertThat(resultado.services()).isEmpty();
    }
}
