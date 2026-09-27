package com.oscargabriel.financeapp.domain.model;

public record ServiceHealth(String name, HealthStatus status) {

    public static ServiceHealth up(String name) {
        return new ServiceHealth(name, HealthStatus.UP);
    }

    public static ServiceHealth down(String name) {
        return new ServiceHealth(name, HealthStatus.DOWN);
    }
}
