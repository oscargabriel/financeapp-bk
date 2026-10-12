package com.oscargabriel.financeapp.app.puente;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.core.domain.port.in.CatchUpSeriesPort;
import com.oscargabriel.financeapp.ia.domain.port.out.SeriesCatchUpPort;

import reactor.core.publisher.Mono;

/** Puente ia → core: las series se ponen al dia donde viven, antes de que el asistente lea (FA-148). */
@Component
@AllArgsConstructor
public class SeriesDelAsistenteAdapter implements SeriesCatchUpPort {

    private final CatchUpSeriesPort series;

    @Override
    public Mono<Void> catchUp(UUID userId) {
        return series.ponerAlDia(userId);
    }
}
