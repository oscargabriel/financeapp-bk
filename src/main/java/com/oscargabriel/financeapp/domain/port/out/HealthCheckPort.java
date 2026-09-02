package com.oscargabriel.financeapp.domain.port.out;

import reactor.core.publisher.Mono;

/**
 * Un servicio externo del que depende la aplicacion. Cada implementacion se registra como bean y
 * el caso de uso de status las recibe todas: agregar un servicio al /status es crear un adaptador.
 */
public interface HealthCheckPort {

    String serviceName();

    /** Completa si el servicio responde; emite error si no. No decide UP/DOWN: eso lo hace el caso de uso. */
    Mono<Void> ping();
}
