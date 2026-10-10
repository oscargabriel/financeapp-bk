package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.RecurrenceMother.HOY;
import static com.oscargabriel.financeapp.support.RecurrenceMother.SERIE_ID;
import static com.oscargabriel.financeapp.support.RecurrenceMother.unParche;
import static com.oscargabriel.financeapp.support.RecurrenceMother.unaSerie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Frequency;
import com.oscargabriel.financeapp.domain.model.Recurrence;
import com.oscargabriel.financeapp.domain.model.RecurrenceScope;
import com.oscargabriel.financeapp.domain.model.RecurrenceTemplateChange;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.UserProfile;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateRecurrenceUseCaseTest {

    /** Viernes 9 de octubre a las 10:00 en Bogota. */
    private static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");

    private static final UUID USER_ID = TransactionMother.USER_ID;

    private static final Instant PROXIMA = Instant.parse("2026-10-15T05:00:00Z");

    @Mock
    private AccountQueryPort cuentas;

    @Mock
    private CategoryQueryPort categorias;

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private RecurrenceRepositoryPort series;

    @Captor
    private ArgumentCaptor<Recurrence> guardada;

    @Captor
    private ArgumentCaptor<RecurrenceTemplateChange> cambios;

    @Captor
    private ArgumentCaptor<List<Transaction>> rehechas;

    private UpdateRecurrenceUseCase casoDeUso;

    @BeforeEach
    void escenario() {
        when(cuentas.findByUser(USER_ID, true)).thenReturn(Flux.fromIterable(TransactionMother.cuentasDelUsuario()));
        when(categorias.findActiveByUser(eq(USER_ID), any()))
                .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
        when(usuarios.findActiveProfile(USER_ID)).thenReturn(Mono.just(
                new UserProfile(USER_ID, "ana@ejemplo.com", "Ana", null, null, "COP", "America/Bogota")));
        when(series.update(any(), any(), any(), any(), any())).thenReturn(Mono.just(true));
        when(series.findNextOccurrence(SERIE_ID)).thenReturn(Mono.just(PROXIMA));
        casoDeUso = new UpdateRecurrenceUseCase(cuentas, categorias, usuarios, series,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void unaSerieInexistenteAjenaOCanceladaEs404SobreId() {
        when(series.findActiveByIdAndUser(SERIE_ID, USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.ALL).amount("1").build()))
                .verifyErrorSatisfies(e -> {
                    BadRequestException error = (BadRequestException) e;
                    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(error.getErrorResponse().getErrors()).extracting(ErrorDetail::getCode,
                            ErrorDetail::getField).containsExactly(tuple("NOT_FOUND", "id"));
                });

        verify(series, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void cambiarElMontoPasaSoloElCambioConSuAlcanceYNoRehaceNada() {
        guardadaEs(unaSerie().veces(3).creadas(3).build());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID,
                        unParche(RecurrenceScope.FUTURE).amount("35000").build()))
                .assertNext(vista -> {
                    assertThat(vista.recurrence().amount()).isEqualByComparingTo("35000");
                    assertThat(vista.nextOccurrenceAt()).isEqualTo(PROXIMA);
                })
                .verifyComplete();

        verify(series).update(guardada.capture(), eq(RecurrenceScope.FUTURE), eq(AHORA), cambios.capture(), isNull());
        assertThat(cambios.getValue()).isEqualTo(new RecurrenceTemplateChange(null, null,
                new BigDecimal("35000"), null));
        assertThat(guardada.getValue().generatedCount()).isEqualTo(3);
    }

    @Test
    void cambiarLaCuentaYLaDescripcionLasValidaYRecortaLaDescripcion() {
        guardadaEs(unaSerie().build());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.ALL)
                        .accountId(TransactionMother.DESTINO_ID.toString()).description("  Netflix premium  ").build()))
                .expectNextCount(1)
                .verifyComplete();

        verify(series).update(guardada.capture(), eq(RecurrenceScope.ALL), eq(AHORA), cambios.capture(), isNull());
        assertThat(cambios.getValue()).isEqualTo(new RecurrenceTemplateChange(TransactionMother.DESTINO_ID, null,
                null, "Netflix premium"));
        assertThat(guardada.getValue().accountId()).isEqualTo(TransactionMother.DESTINO_ID);
    }

    @Test
    void unaCuentaAjenaEsErrorSobreAccountIdYNoGuarda() {
        guardadaEs(unaSerie().build());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.ALL)
                        .accountId(TransactionMother.AJENA_ID.toString()).build()))
                .verifyErrorSatisfies(e -> assertThat(campos(e)).containsExactly("accountId"));

        verify(series, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void unaCategoriaQueNoAplicaAlTipoDeLaSerieEsErrorSobreCategoryId() {
        guardadaEs(unaSerie().build());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.ALL)
                        .categoryId(TransactionMother.SALARIO_ID.toString()).build()))
                .verifyErrorSatisfies(e -> assertThat(campos(e)).containsExactly("categoryId"));
    }

    @Test
    void cambiarLaPeriodicidadRehaceLasFuturasDesdeMananaConservandoElTotal() {
        guardadaEs(unaSerie().mensual(1, 1, LocalDate.of(2026, 9, 1)).veces(6).creadas(6).build());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.FUTURE)
                        .frequency(Frequency.WEEKLY).dayOfWeek(DayOfWeek.FRIDAY).build()))
                .assertNext(vista -> assertThat(vista.nextOccurrenceAt())
                        .isEqualTo(Instant.parse("2026-10-16T05:00:00Z")))
                .verifyComplete();

        verify(series).update(guardada.capture(), eq(RecurrenceScope.FUTURE), eq(AHORA), any(), rehechas.capture());
        Recurrence serie = guardada.getValue();
        assertThat(serie.rule().frequency()).isEqualTo(Frequency.WEEKLY);
        assertThat(serie.rule().dayOfMonth()).isNull();
        assertThat(serie.rule().startDate()).isEqualTo(HOY.plusDays(1));
        assertThat(serie.priorCount()).isEqualTo(2);
        assertThat(serie.generatedCount()).isEqualTo(4);
        assertThat(rehechas.getValue()).extracting(Transaction::occurredAt).containsExactly(
                Instant.parse("2026-10-16T05:00:00Z"), Instant.parse("2026-10-23T05:00:00Z"),
                Instant.parse("2026-10-30T05:00:00Z"), Instant.parse("2026-11-06T05:00:00Z"));
        assertThat(rehechas.getValue()).allSatisfy(t -> assertThat(t.recurrenceId()).isEqualTo(SERIE_ID));
    }

    @Test
    void cambiarSoloElDiaDeUnaSemanalConservaLaFrecuenciaYRehace() {
        guardadaEs(unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY).build());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.FUTURE)
                        .dayOfWeek(DayOfWeek.MONDAY).build()))
                .expectNextCount(1)
                .verifyComplete();

        verify(series).update(guardada.capture(), any(), any(), any(), rehechas.capture());
        assertThat(guardada.getValue().rule().dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(rehechas.getValue()).extracting(Transaction::occurredAt)
                .containsExactly(Instant.parse("2026-10-12T05:00:00Z"));
    }

    @Test
    void mandarLaMismaReglaNoRehaceNada() {
        guardadaEs(unaSerie().build());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.ALL)
                        .frequency(Frequency.MONTHLY).dayOfMonth(15).interval(1).build()))
                .expectNextCount(1)
                .verifyComplete();

        verify(series).update(any(), any(), any(), any(), isNull());
    }

    @Test
    void pasarAMensualSinDiaDelMesEsErrorSobreDayOfMonth() {
        guardadaEs(unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY).build());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.FUTURE)
                        .frequency(Frequency.MONTHLY).build()))
                .verifyErrorSatisfies(e -> assertThat(campos(e)).containsExactly("dayOfMonth"));
    }

    @Test
    void unDiaDeLaSemanaEnUnaSerieQueQuedaMensualEsErrorSobreDayOfWeek() {
        guardadaEs(unaSerie().build());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.FUTURE)
                        .dayOfWeek(DayOfWeek.MONDAY).build()))
                .verifyErrorSatisfies(e -> assertThat(campos(e)).containsExactly("dayOfWeek"));
    }

    @Test
    void elIntervaloSeValidaContraLaFrecuenciaResultante() {
        guardadaEs(unaSerie().semanal(20, DayOfWeek.FRIDAY, HOY).build());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.FUTURE)
                        .frequency(Frequency.MONTHLY).dayOfMonth(5).build()))
                .verifyErrorSatisfies(e -> assertThat(campos(e)).containsExactly("interval"));
    }

    @Test
    void siLaSerieDejoDeEstarActivaAlGuardarEs404() {
        guardadaEs(unaSerie().build());
        when(series.update(any(), any(), any(), any(), any())).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.ALL).amount("1").build()))
                .verifyErrorSatisfies(e -> assertThat(((BadRequestException) e).getHttpStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void unaSinFinSinOcurrenciaFuturaExistenteDevuelveLaQueVaACrear() {
        guardadaEs(unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY.minusDays(7)).creadas(3).build());
        when(series.findNextOccurrence(SERIE_ID)).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.ALL).amount("1").build()))
                .assertNext(vista -> assertThat(vista.nextOccurrenceAt())
                        .isEqualTo(Instant.parse("2026-10-23T05:00:00Z")))
                .verifyComplete();
    }

    @Test
    void unaConFinSinOcurrenciaFuturaNoTieneProxima() {
        guardadaEs(unaSerie().veces(1).creadas(1).build());
        when(series.findNextOccurrence(SERIE_ID)).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.update(USER_ID, SERIE_ID, unParche(RecurrenceScope.ALL).amount("1").build()))
                .assertNext(vista -> assertThat(vista.nextOccurrenceAt()).isNull())
                .verifyComplete();
    }

    private void guardadaEs(Recurrence serie) {
        when(series.findActiveByIdAndUser(SERIE_ID, USER_ID)).thenReturn(Mono.just(serie));
    }

    private static List<String> campos(Throwable e) {
        BadRequestException error = (BadRequestException) e;
        assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        return error.getErrorResponse().getErrors().stream().map(ErrorDetail::getField).toList();
    }
}
