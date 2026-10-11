package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.AccountMother.USER_ID;
import static com.oscargabriel.financeapp.support.AccountMother.efectivo;
import static com.oscargabriel.financeapp.support.AccountMother.visa;
import static com.oscargabriel.financeapp.support.BalanceMother.sumasDelEscenario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.BalanceQueryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GetBalanceUseCaseTest {

    /** 6 de octubre de 2026 a las 22:00 en Bogota, ya el 7 en UTC: el mes del reloj es octubre. */
    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-10-07T03:00:00Z"), ZoneId.of("America/Bogota"));

    private static final LocalDate PRIMERO = LocalDate.of(2026, 10, 1);
    private static final LocalDate ULTIMO = LocalDate.of(2026, 10, 31);

    @Mock
    private BalanceQueryPort query;

    @Mock
    private AccountQueryPort accounts;

    @Mock
    private SeriesAlDia alDia;

    private GetBalanceUseCase casoDeUso;

    @BeforeEach
    void setUp() {
        lenient().when(alDia.ponerAlDia(any())).thenReturn(Mono.empty());
        casoDeUso = new GetBalanceUseCase(query, accounts, RELOJ, alDia);
    }

    @Test
    void sinRangoConsultaElMesDelReloj() {
        when(query.findSums(USER_ID, PRIMERO, ULTIMO)).thenReturn(Mono.just(sumasDelEscenario()));
        when(accounts.findByUser(USER_ID, false)).thenReturn(Flux.empty());

        StepVerifier.create(casoDeUso.get(USER_ID, null, null))
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
        when(query.findSums(USER_ID, desde, hasta)).thenReturn(Mono.just(sumasDelEscenario()));
        when(accounts.findByUser(USER_ID, false)).thenReturn(Flux.empty());

        StepVerifier.create(casoDeUso.get(USER_ID, desde, hasta))
                .assertNext(saldo -> assertThat(saldo.from()).isEqualTo(desde))
                .verifyComplete();

        verify(query).findSums(USER_ID, desde, hasta);
    }

    @Test
    void juntaLasSumasConLasCuentasActivas() {
        when(query.findSums(any(), any(), any())).thenReturn(Mono.just(sumasDelEscenario()));
        when(accounts.findByUser(USER_ID, false)).thenReturn(Flux.just(efectivo(), visa()));

        StepVerifier.create(casoDeUso.get(USER_ID, PRIMERO, ULTIMO))
                .assertNext(saldo -> {
                    assertThat(saldo.currencyCode()).isEqualTo("COP");
                    assertThat(saldo.period().net()).isEqualByComparingTo("3394500");
                    assertThat(saldo.allTime().net()).isEqualByComparingTo("9129500");
                    assertThat(saldo.accounts()).containsExactly(efectivo(), visa());
                })
                .verifyComplete();
    }

    @Test
    void conSoloFromEsErrorEnToSinConsultar() {
        verificaErrorEn(casoDeUso.get(USER_ID, PRIMERO, null), "to");
    }

    @Test
    void conSoloToEsErrorEnFromSinConsultar() {
        verificaErrorEn(casoDeUso.get(USER_ID, null, ULTIMO), "from");
    }

    @Test
    void unRangoInvertidoEsErrorEnFromSinConsultar() {
        verificaErrorEn(casoDeUso.get(USER_ID, ULTIMO, PRIMERO), "from");
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

        verifyNoInteractions(query, accounts, alDia);
    }

    /** El saldo incluye las ocurrencias atrasadas de las series sin fin: se lee despues de crearlas (FA-107). */
    @Test
    void poneAlDiaLasSeriesAntesDeConsultar() {
        when(alDia.ponerAlDia(USER_ID)).thenReturn(Mono.error(new IllegalStateException("sin base")));

        StepVerifier.create(casoDeUso.get(USER_ID, null, null)).verifyError(IllegalStateException.class);

        verifyNoInteractions(query, accounts);
    }
}
