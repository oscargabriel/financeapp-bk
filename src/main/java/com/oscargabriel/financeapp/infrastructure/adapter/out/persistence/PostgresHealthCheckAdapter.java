package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.port.out.HealthCheckPort;

import reactor.core.publisher.Mono;

@Component
public class PostgresHealthCheckAdapter implements HealthCheckPort {

    private static final String SERVICE_NAME = "postgres";

    private final DatabaseClient databaseClient;

    public PostgresHealthCheckAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public String serviceName() {
        return SERVICE_NAME;
    }

    @Override
    public Mono<Void> ping() {
        return databaseClient.sql("SELECT 1").fetch().first().then();
    }
}
