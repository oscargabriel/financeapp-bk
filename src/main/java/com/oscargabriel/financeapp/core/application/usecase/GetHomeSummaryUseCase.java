package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.HomeSummary;
import com.oscargabriel.financeapp.core.domain.model.MonthRange;
import com.oscargabriel.financeapp.core.domain.port.in.GetHomeSummaryPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.BalanceQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.MonthlySpendingQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionReportQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

/**
 * Compone los puertos de salida de los otros reportes, no sus casos de uso: asi las series se ponen al dia una
 * sola vez y las consultas van en paralelo (design.md de FIN-107).
 */
@Service
@AllArgsConstructor
public class GetHomeSummaryUseCase implements GetHomeSummaryPort {

    private final MonthlySpendingQueryPort meses;
    private final BalanceQueryPort saldos;
    private final AccountQueryPort cuentas;
    private final TransactionReportQueryPort movimientos;
    private final UserRepositoryPort usuarios;
    private final Clock clock;
    private final SeriesAlDia alDia;

    @Override
    public Mono<HomeSummary> get(UUID userId) {
        return alDia.ponerAlDia(userId)
                .then(Mono.defer(() -> ZonaDelUsuario.de(usuarios, userId)))
                .flatMap(zona -> resumen(userId, zona));
    }

    /** El mismo instante decide el mes y deja fuera lo programado, para que las dos fronteras coincidan. */
    private Mono<HomeSummary> resumen(UUID userId, ZoneId zona) {
        Instant ahora = clock.instant();
        YearMonth mes = YearMonth.from(ahora.atZone(zona));
        LocalDate primero = mes.atDay(1);
        LocalDate ultimo = mes.atEndOfMonth();
        return Mono.zip(
                        meses.findByUserAndRange(userId, new MonthRange(mes.minusMonths(HomeSummary.MESES - 1L), mes))
                                .collectList(),
                        saldos.findSums(userId, primero, ultimo),
                        cuentas.findByUser(userId, false).collectList(),
                        movimientos.findLatestExpenses(userId, ahora, HomeSummary.GASTOS).collectList(),
                        movimientos.findTopExpenses(userId, primero, ultimo, ahora, HomeSummary.GASTOS).collectList())
                .map(t -> HomeSummary.of(mes, t.getT2(), t.getT1(), t.getT4(), t.getT5(), t.getT3()));
    }
}
