package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.MonthRange;
import com.oscargabriel.financeapp.domain.model.MonthlySpending;
import com.oscargabriel.financeapp.domain.port.in.GetMonthlySpendingPort;
import com.oscargabriel.financeapp.domain.port.out.MonthlySpendingQueryPort;

import reactor.core.publisher.Flux;

@Service
@AllArgsConstructor
public class GetMonthlySpendingUseCase implements GetMonthlySpendingPort {

    private final MonthlySpendingQueryPort query;
    private final Clock clock;
    private final SeriesAlDia alDia;

    /**
     * El defer mantiene el contrato reactivo: un rango invalido sale como senal de error del Flux,
     * no como excepcion lanzada al ensamblar la cadena. Las series se ponen al dia despues de validar:
     * una peticion invalida no escribe nada (FA-107).
     */
    @Override
    public Flux<MonthlySpending> get(UUID userId, YearMonth from, YearMonth to) {
        return Flux.defer(() -> {
            MonthRange rango = resolveRange(from, to);
            return alDia.ponerAlDia(userId).thenMany(Flux.defer(() -> query.findByUserAndRange(userId, rango)));
        });
    }

    private MonthRange resolveRange(YearMonth from, YearMonth to) {
        try {
            return MonthRange.resolve(from, to, YearMonth.now(clock));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    e.getMessage(), "from", e);
        }
    }
}
