package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface CatchUpSeriesPort {

    /**
     * Registra las ocurrencias vencidas de las series del usuario (FA-107), como hacen los reportes
     * antes de leer. Lo usa quien lee movimientos fuera del core (FA-148).
     */
    Mono<Void> ponerAlDia(UUID userId);
}
