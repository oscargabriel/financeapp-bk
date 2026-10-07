package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.Balance;
import com.oscargabriel.financeapp.domain.port.in.GetBalancePort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.BalanceQueryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class GetBalanceUseCase implements GetBalancePort {

    private final BalanceQueryPort query;
    private final AccountQueryPort accounts;
    private final Clock clock;

    /** El defer hace que un rango invalido salga como senal de error, no al ensamblar la cadena. */
    @Override
    public Mono<Balance> get(UUID userId, LocalDate from, LocalDate to) {
        return Mono.defer(() -> {
            Rango rango = rango(from, to);
            return Mono.zip(query.findSums(userId, rango.from(), rango.to()),
                            accounts.findByUser(userId, false).collectList())
                    .map(t -> Balance.of(rango.from(), rango.to(), t.getT1(), t.getT2()));
        });
    }

    /** Los dos o ninguno: con uno solo no hay un ancho natural que deducir (design.md de FA-75). */
    private Rango rango(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            YearMonth mes = YearMonth.now(clock);
            return new Rango(mes.atDay(1), mes.atEndOfMonth());
        }
        if (to == null) {
            throw invalido("Si se envia from, to es obligatoria", "to");
        }
        if (from == null) {
            throw invalido("Si se envia to, from es obligatoria", "from");
        }
        if (from.isAfter(to)) {
            throw invalido("La fecha inicial (" + from + ") es posterior a la fecha final (" + to + ")", "from");
        }
        return new Rango(from, to);
    }

    private static BadRequestException invalido(String mensaje, String campo) {
        return new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, mensaje, campo);
    }

    private record Rango(LocalDate from, LocalDate to) {
    }
}
