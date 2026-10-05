package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.ReportMother.DESDE;
import static com.oscargabriel.financeapp.support.ReportMother.HASTA;
import static com.oscargabriel.financeapp.support.ReportMother.MERCADO_ID;
import static com.oscargabriel.financeapp.support.ReportMother.USER_ID;
import static com.oscargabriel.financeapp.support.ReportMother.unGasto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.TransactionReport.TypeTotal;
import com.oscargabriel.financeapp.domain.model.TransactionReportFilter;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.out.TransactionReportQueryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GetTransactionReportUseCaseTest {

    @Mock
    private TransactionReportQueryPort query;

    @InjectMocks
    private GetTransactionReportUseCase casoDeUso;

    @Captor
    private ArgumentCaptor<TransactionReportFilter> filtro;

    @Test
    void armaElReporteConLaMonedaYLosMovimientosDelPuerto() {
        when(query.findBaseCurrency(USER_ID)).thenReturn(Mono.just("COP"));
        when(query.findByUser(eq(USER_ID), any())).thenReturn(Flux.just(
                unGasto(MERCADO_ID, "Mercado", "85000.0000", "2026-09-02T15:00:00Z")));

        StepVerifier.create(casoDeUso.get(USER_ID, DESDE, HASTA, Set.of(MERCADO_ID), Set.of(TransactionType.EXPENSE)))
                .assertNext(reporte -> {
                    assertThat(reporte.currencyCode()).isEqualTo("COP");
                    assertThat(reporte.transactions()).hasSize(1);
                    assertThat(reporte.totalsByType()).extracting(TypeTotal::type, TypeTotal::total)
                            .containsExactly(tuple(TransactionType.EXPENSE, new BigDecimal("85000.0000")));
                })
                .verifyComplete();

        verify(query).findByUser(eq(USER_ID), filtro.capture());
        assertThat(filtro.getValue()).isEqualTo(new TransactionReportFilter(DESDE, HASTA, Set.of(MERCADO_ID),
                Set.of(TransactionType.EXPENSE)));
    }

    @Test
    void unRangoSinMovimientosDaTotalesEnCero() {
        when(query.findBaseCurrency(USER_ID)).thenReturn(Mono.just("COP"));
        when(query.findByUser(eq(USER_ID), any())).thenReturn(Flux.empty());

        StepVerifier.create(casoDeUso.get(USER_ID, DESDE, HASTA, Set.of(), Set.of()))
                .assertNext(reporte -> {
                    assertThat(reporte.transactions()).isEmpty();
                    assertThat(reporte.totalsByType()).extracting(TypeTotal::total)
                            .containsExactly(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                })
                .verifyComplete();
    }

    @Test
    void unRangoInvertidoEsErrorDeValidacionEnFromSinConsultar() {
        StepVerifier.create(casoDeUso.get(USER_ID, HASTA, DESDE, Set.of(), Set.of()))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                            .containsExactly(tuple("from", ErrorCodes.VALIDATION_ERROR.getCode()));
                })
                .verify();

        verifyNoInteractions(query);
    }
}
