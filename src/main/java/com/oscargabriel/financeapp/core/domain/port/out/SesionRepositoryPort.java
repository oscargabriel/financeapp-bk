package com.oscargabriel.financeapp.core.domain.port.out;

import java.time.Instant;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.NewRefreshToken;
import com.oscargabriel.financeapp.core.domain.model.RotatedSession;

import reactor.core.publisher.Mono;

/** Las sesiones del login: cada una es una familia de refresh tokens que se usan una sola vez. */
public interface SesionRepositoryPort {

    Mono<Void> abrir(NewRefreshToken refresh);

    /**
     * Marca como usado el refresh de {@code hashUsado} y, en la misma operacion, guarda el que lo
     * reemplaza con la familia, la version y el vencimiento del usado. Vacio si el refresh no
     * existe, ya se uso, esta revocado o vencido, o si su usuario ya no esta activo o cambio de
     * version: en ese caso no guarda nada.
     */
    Mono<RotatedSession> rotar(String hashUsado, UUID idNuevo, String hashNuevo, Instant ahora);

    /** Revoca todos los refresh de la sesion de {@code hash}. Completa vacio aunque no exista. */
    Mono<Void> revocarSesionDe(String hash, Instant ahora);
}
