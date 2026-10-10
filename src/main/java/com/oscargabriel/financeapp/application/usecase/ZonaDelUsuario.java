package com.oscargabriel.financeapp.application.usecase;

import java.time.ZoneId;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

/** La zona del perfil, que decide en que dia cae cada ocurrencia de una serie y que es "hoy". */
final class ZonaDelUsuario {

    private ZonaDelUsuario() {
    }

    static Mono<ZoneId> de(UserRepositoryPort usuarios, UUID userId) {
        return usuarios.findActiveProfile(userId).map(perfil -> ZoneId.of(perfil.timezone()));
    }
}
