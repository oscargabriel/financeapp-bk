package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.Balance;
import com.oscargabriel.financeapp.core.domain.model.TransactionReport;
import com.oscargabriel.financeapp.core.domain.model.TransactionReportFilter;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.port.in.TransactionReportPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.BalanceQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionReportQueryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class TransactionReportUseCase implements TransactionReportPort {

    private final TransactionReportQueryPort reportes;
    private final BalanceQueryPort saldos;
    private final AccountQueryPort accounts;
    private final Clock clock;
    private final SeriesAlDia alDia;

    /**
     * El defer hace que un rango invertido salga como senal de error, no al ensamblar la cadena. Las
     * series se ponen al dia despues de validar: una peticion invalida no escribe nada (FA-107).
     */
    @Override
    public Mono<TransactionReport> transactions(UUID userId, LocalDate from, LocalDate to, Set<UUID> categoryIds,
            Set<UUID> accountIds, Set<TransactionType> types) {
        return Mono.defer(() -> {
            TransactionReportFilter filtro = filtro(from, to, categoryIds, accountIds, types);
            return alDia.ponerAlDia(userId)
                    .then(Mono.defer(() -> Mono.zip(reportes.findBaseCurrency(userId),
                            reportes.findByUser(userId, filtro).collectList())))
                    .map(t -> TransactionReport.of(t.getT1(), filtro, t.getT2(), clock.instant()));
        });
    }

    /**
     * El defer hace que un rango invalido salga como senal de error, no al ensamblar la cadena. Las
     * series se ponen al dia despues de validar: una peticion invalida no escribe nada (FA-107).
     */
    @Override
    public Mono<Balance> balance(UUID userId, LocalDate from, LocalDate to) {
        return Mono.defer(() -> {
            Rango rango = rango(from, to);
            return alDia.ponerAlDia(userId).then(Mono.defer(() -> Mono.zip(
                            saldos.findSums(userId, rango.from(), rango.to()),
                            accounts.findByUser(userId, false).collectList())))
                    .map(t -> Balance.of(rango.from(), rango.to(), t.getT1(), t.getT2()));
        });
    }

    private static TransactionReportFilter filtro(LocalDate from, LocalDate to, Set<UUID> categoryIds,
            Set<UUID> accountIds, Set<TransactionType> types) {
        try {
            return new TransactionReportFilter(from, to, categoryIds, accountIds, types);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    e.getMessage(), "from", e);
        }
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
