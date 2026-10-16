package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.AccountMother.USER_ID;
import static com.oscargabriel.financeapp.core.support.AccountMother.visa;
import static com.oscargabriel.financeapp.core.support.BalanceMother.sumasDelEscenario;
import static com.oscargabriel.financeapp.core.support.MonthlySpendingMother.unMes;
import static com.oscargabriel.financeapp.core.support.ReportMother.MERCADO_ID;
import static com.oscargabriel.financeapp.core.support.ReportMother.unGasto;
import static com.oscargabriel.financeapp.core.support.UserMother.unPerfil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.core.domain.model.HomeSummary;
import com.oscargabriel.financeapp.core.domain.model.MonthRange;
import com.oscargabriel.financeapp.core.domain.model.ReportedTransaction;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.BalanceQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.MonthlySpendingQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionReportQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GetHomeSummaryUseCaseTest {

    /** En UTC ya es 1 de noviembre; en Bogota, la zona del perfil, todavia son las 22:00 del 31 de octubre. */
    private static final Instant AHORA = Instant.parse("2026-11-01T03:00:00Z");

    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final LocalDate PRIMERO = LocalDate.of(2026, 10, 1);
    private static final LocalDate ULTIMO = LocalDate.of(2026, 10, 31);

    @Mock
    private MonthlySpendingQueryPort meses;

    @Mock
    private BalanceQueryPort saldos;

    @Mock
    private AccountQueryPort cuentas;

    @Mock
    private TransactionReportQueryPort movimientos;

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private SeriesAlDia alDia;

    private GetHomeSummaryUseCase casoDeUso;

    @BeforeEach
    void setUp() {
        casoDeUso = new GetHomeSummaryUseCase(meses, saldos, cuentas, movimientos, usuarios, RELOJ, alDia);
    }

    @Test
    void consultaElMesEnCursoEnLaZonaDelUsuarioYComponeElResumen() {
        ReportedTransaction ultimo = unGasto(MERCADO_ID, "Mercado", "50000", "2026-10-31T20:00:00Z");
        ReportedTransaction mayor = unGasto(MERCADO_ID, "Mercado", "1200000", "2026-10-01T13:00:00Z");
        when(alDia.ponerAlDia(USER_ID)).thenReturn(Mono.empty());
        when(usuarios.findActiveProfile(USER_ID)).thenReturn(Mono.just(unPerfil()));
        when(meses.findByUserAndRange(USER_ID, new MonthRange(YearMonth.of(2026, 5), OCTUBRE)))
                .thenReturn(Flux.just(unMes(OCTUBRE, "1905500", "5300000")));
        when(saldos.findSums(USER_ID, PRIMERO, ULTIMO)).thenReturn(Mono.just(sumasDelEscenario()));
        when(cuentas.findByUser(USER_ID, false)).thenReturn(Flux.just(visa()));
        when(movimientos.findLatestExpenses(USER_ID, AHORA, 5)).thenReturn(Flux.just(ultimo));
        when(movimientos.findTopExpenses(USER_ID, PRIMERO, ULTIMO, AHORA, 5)).thenReturn(Flux.just(mayor));

        StepVerifier.create(casoDeUso.get(USER_ID))
                .assertNext(resumen -> {
                    assertThat(resumen.month()).isEqualTo(OCTUBRE);
                    assertThat(resumen.currencyCode()).isEqualTo("COP");
                    assertThat(resumen.spent()).isEqualByComparingTo("1905500");
                    assertThat(resumen.latestExpenses()).containsExactly(ultimo);
                    assertThat(resumen.topExpenses()).extracting(HomeSummary.TopExpense::expense)
                            .containsExactly(mayor);
                    assertThat(resumen.accounts()).extracting(HomeSummary.AccountUsage::account)
                            .containsExactly(visa());
                    assertThat(resumen.debitBalance()).isEqualByComparingTo("12375000");
                })
                .verifyComplete();
    }

    @Test
    void noConsultaNadaHastaQueLasSeriesEstenAlDia() {
        when(alDia.ponerAlDia(USER_ID)).thenReturn(Mono.never());

        StepVerifier.create(casoDeUso.get(USER_ID))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(50))
                .thenCancel()
                .verify();

        verifyNoInteractions(usuarios, meses, saldos, cuentas, movimientos);
    }
}
