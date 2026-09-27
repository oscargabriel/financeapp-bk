package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.MonthRange;
import com.oscargabriel.financeapp.domain.port.out.MonthlySpendingQueryPort;
import com.oscargabriel.financeapp.support.MonthlySpendingMother;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GetMonthlySpendingUseCaseTest {

    /** 15 de septiembre de 2026, 10:00 en Bogota: el mes actual del caso de uso es 2026-09. */
    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-09-15T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private MonthlySpendingQueryPort query;

    @Captor
    private ArgumentCaptor<MonthRange> rangoCapturado;

    @Test
    void consultaLosDoceMesesQueTerminanEnElMesActualCuandoNoLleganExtremos() {
        when(query.findByUserAndRange(eq(MonthlySpendingMother.USER_ID), any())).thenReturn(Flux.empty());

        StepVerifier.create(useCase().get(MonthlySpendingMother.USER_ID, null, null))
                .verifyComplete();

        org.mockito.Mockito.verify(query)
                .findByUserAndRange(eq(MonthlySpendingMother.USER_ID), rangoCapturado.capture());
        assertThat(rangoCapturado.getValue().from()).isEqualTo(YearMonth.of(2025, 10));
        assertThat(rangoCapturado.getValue().to()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void consultaElRangoRecibidoCuandoLleganLosDosExtremos() {
        when(query.findByUserAndRange(eq(MonthlySpendingMother.USER_ID), any())).thenReturn(Flux.empty());

        StepVerifier.create(useCase().get(
                        MonthlySpendingMother.USER_ID, YearMonth.of(2026, 1), YearMonth.of(2026, 3)))
                .verifyComplete();

        org.mockito.Mockito.verify(query)
                .findByUserAndRange(eq(MonthlySpendingMother.USER_ID), rangoCapturado.capture());
        assertThat(rangoCapturado.getValue())
                .isEqualTo(new MonthRange(YearMonth.of(2026, 1), YearMonth.of(2026, 3)));
    }

    @Test
    void emiteLosMesesEnElOrdenQueEntregaLaVista() {
        when(query.findByUserAndRange(eq(MonthlySpendingMother.USER_ID), any())).thenReturn(
                Flux.just(
                        MonthlySpendingMother.unMesConMeta(YearMonth.of(2026, 9)),
                        MonthlySpendingMother.unMesSinMeta(YearMonth.of(2026, 8))));

        StepVerifier.create(useCase().get(MonthlySpendingMother.USER_ID, null, null))
                .assertNext(mes -> assertThat(mes.periodMonth()).isEqualTo(YearMonth.of(2026, 9)))
                .assertNext(mes -> assertThat(mes.periodMonth()).isEqualTo(YearMonth.of(2026, 8)))
                .verifyComplete();
    }

    @Test
    void emiteBadRequestCuandoElInicioEsPosteriorAlFin() {
        StepVerifier.create(useCase().get(
                        MonthlySpendingMother.USER_ID, YearMonth.of(2026, 5), YearMonth.of(2026, 1)))
                .verifyErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BadRequestException.class);
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus().value()).isEqualTo(400);
                    assertThat(bre.getErrorResponse().getErrors())
                            .singleElement()
                            .satisfies(detalle -> {
                                assertThat(detalle.getCode()).isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode());
                                assertThat(detalle.getField()).isEqualTo("from");
                            });
                });
    }

    @Test
    void noConsultaLaVistaCuandoElRangoEsInvalido() {
        StepVerifier.create(useCase().get(
                        MonthlySpendingMother.USER_ID, YearMonth.of(2026, 5), YearMonth.of(2026, 1)))
                .verifyError(BadRequestException.class);

        org.mockito.Mockito.verifyNoInteractions(query);
    }

    private GetMonthlySpendingUseCase useCase() {
        return new GetMonthlySpendingUseCase(query, RELOJ);
    }
}
