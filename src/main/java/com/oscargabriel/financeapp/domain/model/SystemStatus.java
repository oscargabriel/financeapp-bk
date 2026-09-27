package com.oscargabriel.financeapp.domain.model;

import java.util.List;

public record SystemStatus(HealthStatus status, List<ServiceHealth> services) {

    public SystemStatus {
        services = List.copyOf(services);
    }

    public static SystemStatus of(List<ServiceHealth> services) {
        boolean algunoAbajo = services.stream().anyMatch(s -> s.status() == HealthStatus.DOWN);
        return new SystemStatus(algunoAbajo ? HealthStatus.DOWN : HealthStatus.UP, services);
    }
}
