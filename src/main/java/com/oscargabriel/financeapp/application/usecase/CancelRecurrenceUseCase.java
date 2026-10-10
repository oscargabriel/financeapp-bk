package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.port.in.CancelRecurrencePort;
import com.oscargabriel.financeapp.domain.port.out.RecurrenceRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class CancelRecurrenceUseCase implements CancelRecurrencePort {

    private final RecurrenceRepositoryPort series;
    private final Clock clock;

    /** Una sola escritura filtrada por usuario y estado: cero filas es 404, sin una lectura previa. */
    @Override
    public Mono<Void> cancel(UUID userId, UUID recurrenceId) {
        return Mono.defer(() -> series.cancel(recurrenceId, userId, clock.instant()))
                .flatMap(cancelada -> cancelada
                        ? Mono.<Void>empty()
                        : Mono.error(UpdateRecurrenceUseCase.noEncontrada()));
    }
}
