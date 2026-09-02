package com.oscargabriel.financeapp.application.usecase;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.ServiceHealth;
import com.oscargabriel.financeapp.domain.model.SystemStatus;
import com.oscargabriel.financeapp.domain.port.in.CheckSystemStatusPort;
import com.oscargabriel.financeapp.domain.port.out.HealthCheckPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CheckSystemStatusUseCase implements CheckSystemStatusPort {

    private static final Logger log = LoggerFactory.getLogger(CheckSystemStatusUseCase.class);

    private final List<HealthCheckPort> healthChecks;
    private final Duration checkTimeout;

    public CheckSystemStatusUseCase(
            List<HealthCheckPort> healthChecks,
            @Value("${status.check-timeout}") Duration checkTimeout) {
        this.healthChecks = List.copyOf(healthChecks);
        this.checkTimeout = checkTimeout;
    }

    @Override
    public Mono<SystemStatus> check() {
        return Flux.fromIterable(healthChecks)
                .flatMapSequential(this::verificar)
                .collectList()
                .map(SystemStatus::of);
    }

    /** El timeout y la traduccion de fallo a DOWN viven aqui para que ningun adaptador los repita. */
    private Mono<ServiceHealth> verificar(HealthCheckPort healthCheck) {
        String servicio = healthCheck.serviceName();
        return healthCheck.ping()
                .timeout(checkTimeout)
                .then(Mono.just(ServiceHealth.up(servicio)))
                .onErrorResume(e -> {
                    log.warn("Health check fallido para servicio={}: {}", servicio, e.toString());
                    return Mono.just(ServiceHealth.down(servicio));
                });
    }
}
