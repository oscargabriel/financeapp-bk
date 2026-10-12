package com.oscargabriel.financeapp.core.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.port.in.CatchUpSeriesPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class CatchUpSeriesUseCase implements CatchUpSeriesPort {

    private final SeriesAlDia alDia;

    @Override
    public Mono<Void> ponerAlDia(UUID userId) {
        return alDia.ponerAlDia(userId);
    }
}
