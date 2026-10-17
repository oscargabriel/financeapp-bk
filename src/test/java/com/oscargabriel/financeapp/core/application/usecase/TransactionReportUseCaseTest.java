package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.AccountMother.efectivo;
import static com.oscargabriel.financeapp.core.support.AccountMother.visa;
import static com.oscargabriel.financeapp.core.support.BalanceMother.sumasDelEscenario;
import static com.oscargabriel.financeapp.core.support.ReportMother.CUENTA_ID;
import static com.oscargabriel.financeapp.core.support.ReportMother.DESDE;
import static com.oscargabriel.financeapp.core.support.ReportMother.HASTA;
import static com.oscargabriel.financeapp.core.support.ReportMother.MERCADO_ID;
import static com.oscargabriel.financeapp.core.support.ReportMother.USER_ID;
import static com.oscargabriel.financeapp.core.support.ReportMother.unGasto;
import static com.oscargabriel.financeapp.core.support.ReportMother.unaTransferenciaEnDolares;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.ReportedTransaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionReport.TypeTotal;
import com.oscargabriel.financeapp.core.domain.model.TransactionReportFilter;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.BalanceQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionReportQueryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TransactionReportUseCaseTest {

    @Mock
    private TransactionReportQueryPort reportes;

    @Mock
    private BalanceQueryPort saldos;

    @Mock
    private AccountQueryPort accounts;

    @Mock
    private SeriesAlDia alDia;

    @Captor
    private ArgumentCaptor<TransactionReportFilter> filtro;

    @BeforeEach
    void setUp() {
        lenient().when(alDia.ponerAlDia(any())).thenReturn(Mono.empty());
    }

    private TransactionReportUseCase useCase(Clock clock) {
        return new TransactionReportUseCase(reportes, saldos, accounts, clock, alDia);
    }

    @Nested
    class Transacciones {

        /** Despues de todo el mes de prueba: ningun movimiento de ReportMother sale programado. */
        private static final Instant AHORA = Instant.parse("2026-10-01T00:00:00Z");

        private TransactionReportUseCase casoDeUso;

        @BeforeEach
        void setUp() {
            casoDeUso = useCase(Clock.fixed(AHORA, ZoneOffset.UTC));
        }

        @Test
        void armaElReporteAlInstanteDelRelojYNoSumaLoQueOcurreDespues() {
            when(reportes.findBaseCurrency(USER_ID)).thenReturn(Mono.just("COP"));
            when(reportes.findByUser(eq(USER_ID), any())).thenReturn(Flux.just(
                    unGasto(MERCADO_ID, "Mercado", "85000.0000", "2026-09-02T15:00:00Z"),
                    unGasto(MERCADO_ID, "Mercado", "40000.0000", "2026-10-15T15:00:00Z")));

            StepVerifier.create(casoDeUso.transactions(USER_ID, DESDE, HASTA, Set.of(), Set.of(), Set.of()))
                    .assertNext(reporte -> {
                        assertThat(reporte.asOf()).isEqualTo(AHORA);
                        assertThat(reporte.transactions()).hasSize(2);
                        assertThat(reporte.totalsByType()).extracting(TypeTotal::type, TypeTotal::total)
                                .contains(tuple(TransactionType.EXPENSE, new BigDecimal("85000.0000")));
                    })
                    .verifyComplete();
        }

        @Test
        void armaElReporteConLaMonedaYLosMovimientosDelPuertoYLePasaTodosLosFiltros() {
            when(reportes.findBaseCurrency(USER_ID)).thenReturn(Mono.just("COP"));
            when(reportes.findByUser(eq(USER_ID), any())).thenReturn(Flux.just(
                    unGasto(MERCADO_ID, "Mercado", "85000.0000", "2026-09-02T15:00:00Z")));

            StepVerifier.create(casoDeUso.transactions(USER_ID, DESDE, HASTA, Set.of(MERCADO_ID), Set.of(CUENTA_ID),
                    Set.of(TransactionType.EXPENSE)))
                    .assertNext(reporte -> {
                        assertThat(reporte.currencyCode()).isEqualTo("COP");
                        assertThat(reporte.transactions()).hasSize(1);
                        assertThat(reporte.totalsByType()).extracting(TypeTotal::type, TypeTotal::total)
                                .containsExactly(tuple(TransactionType.EXPENSE, new BigDecimal("85000.0000")));
                    })
                    .verifyComplete();

            verify(reportes).findByUser(eq(USER_ID), filtro.capture());
            assertThat(filtro.getValue()).isEqualTo(new TransactionReportFilter(DESDE, HASTA, Set.of(MERCADO_ID),
                    Set.of(CUENTA_ID), Set.of(TransactionType.EXPENSE)));
        }

        @Test
        void conservaElMontoDeDestinoQueTraeElPuerto() {
            when(reportes.findBaseCurrency(USER_ID)).thenReturn(Mono.just("COP"));
            when(reportes.findByUser(eq(USER_ID), any())).thenReturn(Flux.just(
                    unaTransferenciaEnDolares("2026-09-10T20:00:00Z"),
                    unGasto(MERCADO_ID, "Mercado", "85000.0000", "2026-09-02T15:00:00Z")));

            StepVerifier.create(casoDeUso.transactions(USER_ID, DESDE, HASTA, Set.of(), Set.of(), Set.of()))
                    .assertNext(reporte -> assertThat(reporte.transactions())
                            .extracting(ReportedTransaction::type, ReportedTransaction::destinationAmount)
                            .containsExactly(
                                    tuple(TransactionType.TRANSFER, new BigDecimal("410000.0000")),
                                    tuple(TransactionType.EXPENSE, null)))
                    .verifyComplete();
        }

        @Test
        void unRangoSinMovimientosDaTotalesEnCero() {
            when(reportes.findBaseCurrency(USER_ID)).thenReturn(Mono.just("COP"));
            when(reportes.findByUser(eq(USER_ID), any())).thenReturn(Flux.empty());

            StepVerifier.create(casoDeUso.transactions(USER_ID, DESDE, HASTA, Set.of(), Set.of(), Set.of()))
                    .assertNext(reporte -> {
                        assertThat(reporte.transactions()).isEmpty();
                        assertThat(reporte.totalsByType()).extracting(TypeTotal::total)
                                .containsExactly(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                    })
                    .verifyComplete();
        }

        @Test
        void unRangoInvertidoEsErrorDeValidacionEnFromSinConsultar() {
            StepVerifier.create(casoDeUso.transactions(USER_ID, HASTA, DESDE, Set.of(), Set.of(), Set.of()))
                    .expectErrorSatisfies(error -> {
                        BadRequestException bre = (BadRequestException) error;
                        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(bre.getErrorResponse().getErrors())
                                .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                                .containsExactly(tuple("from", ErrorCodes.VALIDATION_ERROR.getCode()));
                    })
                    .verify();

            verifyNoInteractions(reportes, alDia);
        }

        /** El reporte incluye las ocurrencias atrasadas de las series sin fin: se lee despues de crearlas (FA-107). */
        @Test
        void poneAlDiaLasSeriesAntesDeConsultar() {
            when(alDia.ponerAlDia(USER_ID)).thenReturn(Mono.error(new IllegalStateException("sin base")));

            StepVerifier.create(casoDeUso.transactions(USER_ID, DESDE, HASTA, Set.of(), Set.of(), Set.of()))
                    .verifyError(IllegalStateException.class);

            verifyNoInteractions(reportes);
        }
    }

    @Nested
    class Balance {

        /** 6 de octubre de 2026 a las 22:00 en Bogota, ya el 7 en UTC: el mes del reloj es octubre. */
        private static final Clock RELOJ = Clock.fixed(
                Instant.parse("2026-10-07T03:00:00Z"), ZoneId.of("America/Bogota"));

        private static final LocalDate PRIMERO = LocalDate.of(2026, 10, 1);
        private static final LocalDate ULTIMO = LocalDate.of(2026, 10, 31);

        private TransactionReportUseCase casoDeUso;

        @BeforeEach
        void setUp() {
            casoDeUso = useCase(RELOJ);
        }

        @Test
        void sinRangoConsultaElMesDelReloj() {
            when(saldos.findSums(USER_ID, PRIMERO, ULTIMO)).thenReturn(Mono.just(sumasDelEscenario()));
            when(accounts.findByUser(USER_ID, false)).thenReturn(Flux.empty());

            StepVerifier.create(casoDeUso.balance(USER_ID, null, null))
                    .assertNext(saldo -> {
                        assertThat(saldo.from()).isEqualTo(PRIMERO);
                        assertThat(saldo.to()).isEqualTo(ULTIMO);
                    })
                    .verifyComplete();
        }

        @Test
        void conRangoConsultaElRangoRecibido() {
            LocalDate desde = LocalDate.of(2026, 9, 2);
            LocalDate hasta = LocalDate.of(2026, 9, 30);
            when(saldos.findSums(USER_ID, desde, hasta)).thenReturn(Mono.just(sumasDelEscenario()));
            when(accounts.findByUser(USER_ID, false)).thenReturn(Flux.empty());

            StepVerifier.create(casoDeUso.balance(USER_ID, desde, hasta))
                    .assertNext(saldo -> assertThat(saldo.from()).isEqualTo(desde))
                    .verifyComplete();

            verify(saldos).findSums(USER_ID, desde, hasta);
        }

        @Test
        void juntaLasSumasConLasCuentasActivas() {
            when(saldos.findSums(any(), any(), any())).thenReturn(Mono.just(sumasDelEscenario()));
            when(accounts.findByUser(USER_ID, false)).thenReturn(Flux.just(efectivo(), visa()));

            StepVerifier.create(casoDeUso.balance(USER_ID, PRIMERO, ULTIMO))
                    .assertNext(saldo -> {
                        assertThat(saldo.currencyCode()).isEqualTo("COP");
                        assertThat(saldo.period().net()).isEqualByComparingTo("3394500");
                        assertThat(saldo.allTime().net()).isEqualByComparingTo("9129500");
                        assertThat(saldo.accounts()).containsExactly(efectivo(), visa());
                    })
                    .verifyComplete();
        }

        @Test
        void elTotalDebitoDeLaBaseLlegaAlSaldo() {
            when(saldos.findSums(any(), any(), any())).thenReturn(Mono.just(sumasDelEscenario()));
            when(accounts.findByUser(USER_ID, false)).thenReturn(Flux.empty());

            StepVerifier.create(casoDeUso.balance(USER_ID, PRIMERO, ULTIMO))
                    .assertNext(saldo -> assertThat(saldo.debitBalance()).isEqualByComparingTo("12375000"))
                    .verifyComplete();
        }

        @Test
        void conSoloFromEsErrorEnToSinConsultar() {
            verificaErrorEn(casoDeUso.balance(USER_ID, PRIMERO, null), "to");
        }

        @Test
        void conSoloToEsErrorEnFromSinConsultar() {
            verificaErrorEn(casoDeUso.balance(USER_ID, null, ULTIMO), "from");
        }

        @Test
        void unRangoInvertidoEsErrorEnFromSinConsultar() {
            verificaErrorEn(casoDeUso.balance(USER_ID, ULTIMO, PRIMERO), "from");
        }

        private void verificaErrorEn(Mono<?> resultado, String campo) {
            StepVerifier.create(resultado)
                    .expectErrorSatisfies(error -> {
                        BadRequestException bre = (BadRequestException) error;
                        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(bre.getErrorResponse().getErrors())
                                .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                                .containsExactly(tuple(campo, ErrorCodes.VALIDATION_ERROR.getCode()));
                    })
                    .verify();

            verifyNoInteractions(saldos, accounts, alDia);
        }

        /** El saldo incluye las ocurrencias atrasadas de las series sin fin: se lee despues de crearlas (FA-107). */
        @Test
        void poneAlDiaLasSeriesAntesDeConsultar() {
            when(alDia.ponerAlDia(USER_ID)).thenReturn(Mono.error(new IllegalStateException("sin base")));

            StepVerifier.create(casoDeUso.balance(USER_ID, null, null)).verifyError(IllegalStateException.class);

            verifyNoInteractions(saldos, accounts);
        }
    }
}
