package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;

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
public class GetMonthlySpendingUseCase implements GetMonthlySpendingPort {

    private final MonthlySpendingQueryPort query;
    private final Clock clock;

    public GetMonthlySpendingUseCase(MonthlySpendingQueryPort query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    /**
     * El defer mantiene el contrato reactivo: un rango invalido sale como senal de error del Flux,
     * no como excepcion lanzada al ensamblar la cadena.
     */
    @Override
    public Flux<MonthlySpending> get(UUID userId, YearMonth from, YearMonth to) {
        return Flux.defer(() -> query.findByUserAndRange(userId, resolveRange(from, to)));
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
