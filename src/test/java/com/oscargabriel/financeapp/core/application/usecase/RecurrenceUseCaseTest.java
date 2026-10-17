package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.RecurrenceMother.HOY;
import static com.oscargabriel.financeapp.core.support.RecurrenceMother.SERIE_ID;
import static com.oscargabriel.financeapp.core.support.RecurrenceMother.unAlta;
import static com.oscargabriel.financeapp.core.support.RecurrenceMother.unParche;
import static com.oscargabriel.financeapp.core.support.RecurrenceMother.unaSerie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.CreateRecurrenceCommand;
import com.oscargabriel.financeapp.core.domain.model.Frequency;
import com.oscargabriel.financeapp.core.domain.model.GroupScope;
import com.oscargabriel.financeapp.core.domain.model.Recurrence;
import com.oscargabriel.financeapp.core.domain.model.RecurrenceStatus;
import com.oscargabriel.financeapp.core.domain.model.RecurrenceTemplateChange;
import com.oscargabriel.financeapp.core.domain.model.RecurrenceView;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.core.support.CurrencyMother;
import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RecurrenceUseCaseTest {

    /** Viernes 9 de octubre a las 10:00 en Bogota. */
    private static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");

    private static final UUID USER_ID = TransactionMother.USER_ID;

    @Mock
    private AccountQueryPort cuentas;

    @Mock
    private CategoryQueryPort categorias;

    @Mock
    private CurrencyQueryPort monedas;

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private RecurrenceRepositoryPort series;

    @Mock
    private SeriesAlDia alDia;

    @Nested
    class Crear {

        @Captor
        private ArgumentCaptor<Recurrence> serieGuardada;

        @Captor
        private ArgumentCaptor<List<Transaction>> ocurrencias;

        @BeforeEach
        void escenario() {
            lenient().when(cuentas.findByUser(USER_ID, true))
                    .thenReturn(Flux.fromIterable(TransactionMother.cuentasDelUsuario()));
            lenient().when(categorias.findActiveByUser(eq(USER_ID), any()))
                    .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
            lenient().when(monedas.findActive()).thenReturn(Flux.just(CurrencyMother.cop(), CurrencyMother.usd()));
            lenient().when(usuarios.findActiveProfile(USER_ID)).thenReturn(perfilEn("America/Bogota"));
            lenient().when(series.save(any(), anyList())).thenReturn(Mono.empty());
        }

        @Test
        void guardaLaSerieConSusOcurrenciasYDevuelveLaProxima() {
            StepVerifier.create(useCase().create(USER_ID, unAlta().build()))
                    .assertNext(vista -> {
                        assertThat(vista.nextOccurrenceAt()).isEqualTo(Instant.parse("2026-10-15T05:00:00Z"));
                        assertThat(vista.recurrence().description()).isEqualTo("Netflix");
                    })
                    .verifyComplete();

            verify(series).save(serieGuardada.capture(), ocurrencias.capture());
            Recurrence serie = serieGuardada.getValue();
            assertThat(serie.id().version()).isEqualTo(7);
            assertThat(serie.userId()).isEqualTo(USER_ID);
            assertThat(serie.currencyCode()).isEqualTo("COP");
            assertThat(serie.generatedCount()).isEqualTo(3);
            assertThat(serie.occurrenceLimit()).isEqualTo(3);
            assertThat(serie.status()).isEqualTo(RecurrenceStatus.ACTIVE);
            assertThat(ocurrencias.getValue())
                    .extracting(Transaction::occurredAt)
                    .containsExactly(Instant.parse("2026-10-15T05:00:00Z"), Instant.parse("2026-11-15T05:00:00Z"),
                            Instant.parse("2026-12-15T05:00:00Z"));
            assertThat(ocurrencias.getValue()).allSatisfy(t -> {
                assertThat(t.recurrenceId()).isEqualTo(serie.id());
                assertThat(t.status()).isEqualTo(TransactionStatus.CONFIRMED);
                assertThat(t.description()).isEqualTo("Netflix");
            });
            assertThat(ocurrencias.getValue()).extracting(Transaction::id).doesNotHaveDuplicates();
        }

        @Test
        void usaLaZonaDelPerfilDelUsuario() {
            when(usuarios.findActiveProfile(USER_ID)).thenReturn(perfilEn("Asia/Tokyo"));

            StepVerifier.create(useCase().create(USER_ID, unAlta().veces(1).build()))
                    .assertNext(vista -> assertThat(vista.nextOccurrenceAt())
                            .isEqualTo(Instant.parse("2026-10-14T15:00:00Z")))
                    .verifyComplete();
        }

        @Test
        void unaSinFinQueEmpezoHaceTresSemanasCreaHastaHoyMasLaSiguiente() {
            CreateRecurrenceCommand alta = unAlta().semanal(DayOfWeek.FRIDAY).desde(HOY.minusDays(21)).sinFin().build();

            StepVerifier.create(useCase().create(USER_ID, alta))
                    .assertNext(vista -> assertThat(vista.nextOccurrenceAt())
                            .isEqualTo(Instant.parse("2026-10-16T05:00:00Z")))
                    .verifyComplete();

            verify(series).save(serieGuardada.capture(), ocurrencias.capture());
            assertThat(ocurrencias.getValue()).hasSize(5);
            assertThat(serieGuardada.getValue().generatedCount()).isEqualTo(5);
        }

        @Test
        void unaConFinYaPasadaNoTieneProxima() {
            CreateRecurrenceCommand alta = unAlta().mensual(1).desde(HOY.minusMonths(3)).veces(2).build();

            StepVerifier.create(useCase().create(USER_ID, alta))
                    .assertNext(vista -> assertThat(vista.nextOccurrenceAt()).isNull())
                    .verifyComplete();
        }

        @Test
        void unaCuentaAjenaEsErrorSobreAccountIdYNoGuardaNada() {
            CreateRecurrenceCommand alta = unAlta().accountId(TransactionMother.AJENA_ID.toString()).build();

            StepVerifier.create(useCase().create(USER_ID, alta))
                    .verifyErrorSatisfies(e -> assertThat(errores(e))
                            .extracting(ErrorDetail::getField, ErrorDetail::getDescription)
                            .containsExactly(tuple("accountId", "La cuenta no existe")));

            verify(series, never()).save(any(), anyList());
        }

        /** FA-123: la serie toma la moneda de su cuenta, y sus ocurrencias tambien. */
        @Test
        void unaCuentaEnUsdCreaLaSerieYSusOcurrenciasEnUsd() {
            CreateRecurrenceCommand alta = unAlta().accountId(TransactionMother.USD_ID.toString()).build();

            StepVerifier.create(useCase().create(USER_ID, alta))
                    .assertNext(vista -> assertThat(vista.recurrence().currencyCode()).isEqualTo("USD"))
                    .verifyComplete();

            verify(series).save(serieGuardada.capture(), ocurrencias.capture());
            assertThat(serieGuardada.getValue().currencyCode()).isEqualTo("USD");
            assertThat(ocurrencias.getValue()).hasSize(3).allSatisfy(t -> {
                assertThat(t.accountId()).isEqualTo(TransactionMother.USD_ID);
                assertThat(t.currencyCode()).isEqualTo("USD");
            });
        }

        @Test
        void unaCuentaEnUnaMonedaQueYaNoEstaActivaEsErrorSobreAccountId() {
            when(monedas.findActive()).thenReturn(Flux.just(CurrencyMother.cop()));
            CreateRecurrenceCommand alta = unAlta().accountId(TransactionMother.USD_ID.toString()).build();

            StepVerifier.create(useCase().create(USER_ID, alta))
                    .verifyErrorSatisfies(e -> assertThat(errores(e))
                            .extracting(ErrorDetail::getField, ErrorDetail::getDescription)
                            .containsExactly(tuple("accountId", "La moneda de la cuenta no esta activa")));

            verify(series, never()).save(any(), anyList());
        }

        @Test
        void unaCategoriaQueNoAplicaAlTipoEsErrorSobreCategoryId() {
            CreateRecurrenceCommand alta = unAlta().categoryId(TransactionMother.SALARIO_ID.toString()).build();

            StepVerifier.create(useCase().create(USER_ID, alta))
                    .verifyErrorSatisfies(e -> assertThat(errores(e)).extracting(ErrorDetail::getField)
                            .containsExactly("categoryId"));
        }

        @Test
        void masDeQuinientasOcurrenciasConFinEsErrorSobreEndDate() {
            CreateRecurrenceCommand alta = unAlta().semanal(DayOfWeek.MONDAY).hasta(HOY.plusYears(10)).build();

            StepVerifier.create(useCase().create(USER_ID, alta))
                    .verifyErrorSatisfies(e -> assertThat(errores(e)).extracting(ErrorDetail::getField)
                            .containsExactly("endDate"));

            verify(series, never()).save(any(), anyList());
        }

        @Test
        void unaSinFinQueEmpiezaDemasiadoAtrasEsErrorSobreStartDate() {
            CreateRecurrenceCommand alta = unAlta().semanal(DayOfWeek.MONDAY).desde(HOY.minusYears(10)).sinFin()
                    .build();

            StepVerifier.create(useCase().create(USER_ID, alta))
                    .verifyErrorSatisfies(e -> assertThat(errores(e)).extracting(ErrorDetail::getField)
                            .containsExactly("startDate"));
        }

        @Test
        void unaSerieSinNingunaOcurrenciaAntesDeSuFinEsErrorSobreEndDate() {
            CreateRecurrenceCommand alta = unAlta().semanal(DayOfWeek.MONDAY).desde(HOY).hasta(HOY.plusDays(1))
                    .build();

            StepVerifier.create(useCase().create(USER_ID, alta))
                    .verifyErrorSatisfies(e -> assertThat(errores(e)).extracting(ErrorDetail::getField)
                            .containsExactly("endDate"));
        }

        private static List<ErrorDetail> errores(Throwable e) {
            assertThat(e).isInstanceOf(BadRequestException.class);
            BadRequestException error = (BadRequestException) e;
            assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            return error.getErrorResponse().getErrors();
        }
    }

    @Nested
    class Listar {

        @BeforeEach
        void escenario() {
            lenient().when(alDia.ponerAlDia(USER_ID)).thenReturn(Mono.empty());
            lenient().when(usuarios.findActiveProfile(USER_ID)).thenReturn(perfilEn("America/Bogota"));
        }

        @Test
        void devuelveLasActivasDelRepositorioSinLeerElPerfil() {
            RecurrenceView primera = new RecurrenceView(unaSerie().id(UUID.randomUUID()).build(),
                    Instant.parse("2026-10-12T05:00:00Z"));
            RecurrenceView segunda = new RecurrenceView(unaSerie().id(UUID.randomUUID()).build(),
                    Instant.parse("2026-10-15T05:00:00Z"));
            when(series.findActiveByUser(USER_ID)).thenReturn(Flux.just(primera, segunda));

            StepVerifier.create(useCase().list(USER_ID))
                    .expectNext(primera, segunda)
                    .verifyComplete();

            verifyNoInteractions(usuarios);
        }

        @Test
        void unaSinFinSinOcurrenciaFuturaUsaLaQueVaACrearYQuedaEnSuLugar() {
            RecurrenceView sinProxima = new RecurrenceView(
                    unaSerie().id(UUID.randomUUID()).semanal(1, DayOfWeek.FRIDAY, HOY.minusDays(7)).creadas(3).build(),
                    null);
            RecurrenceView antes = new RecurrenceView(unaSerie().id(UUID.randomUUID()).build(),
                    Instant.parse("2026-10-15T05:00:00Z"));
            RecurrenceView despues = new RecurrenceView(unaSerie().id(UUID.randomUUID()).build(),
                    Instant.parse("2026-11-15T05:00:00Z"));
            when(series.findActiveByUser(USER_ID)).thenReturn(Flux.just(antes, despues, sinProxima));

            StepVerifier.create(useCase().list(USER_ID).map(RecurrenceView::nextOccurrenceAt))
                    .expectNext(Instant.parse("2026-10-15T05:00:00Z"), Instant.parse("2026-10-23T05:00:00Z"),
                            Instant.parse("2026-11-15T05:00:00Z"))
                    .verifyComplete();
        }

        @Test
        void poneAlDiaLasSeriesAntesDeListar() {
            when(alDia.ponerAlDia(USER_ID)).thenReturn(Mono.error(new IllegalStateException("sin base")));

            StepVerifier.create(useCase().list(USER_ID)).verifyError(IllegalStateException.class);

            verifyNoInteractions(series);
        }
    }

    @Nested
    class Actualizar {

        private static final Instant PROXIMA = Instant.parse("2026-10-15T05:00:00Z");

        @Captor
        private ArgumentCaptor<Recurrence> guardada;

        @Captor
        private ArgumentCaptor<RecurrenceTemplateChange> cambios;

        @Captor
        private ArgumentCaptor<List<Transaction>> rehechas;

        @BeforeEach
        void escenario() {
            lenient().when(cuentas.findByUser(USER_ID, true))
                    .thenReturn(Flux.fromIterable(TransactionMother.cuentasDelUsuario()));
            lenient().when(categorias.findActiveByUser(eq(USER_ID), any()))
                    .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
            lenient().when(usuarios.findActiveProfile(USER_ID)).thenReturn(perfilEn("America/Bogota"));
            lenient().when(series.update(any(), any(), any(), any(), any())).thenReturn(Mono.just(true));
            lenient().when(series.findNextOccurrence(SERIE_ID)).thenReturn(Mono.just(PROXIMA));
        }

        @Test
        void unaSerieInexistenteAjenaOCanceladaEs404SobreId() {
            when(series.findActiveByIdAndUser(SERIE_ID, USER_ID)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.ALL).amount("1").build()))
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

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID,
                            unParche(GroupScope.FUTURE).amount("35000").build()))
                    .assertNext(vista -> {
                        assertThat(vista.recurrence().amount()).isEqualByComparingTo("35000");
                        assertThat(vista.nextOccurrenceAt()).isEqualTo(PROXIMA);
                    })
                    .verifyComplete();

            verify(series).update(guardada.capture(), eq(GroupScope.FUTURE), eq(AHORA), cambios.capture(),
                    isNull());
            assertThat(cambios.getValue()).isEqualTo(new RecurrenceTemplateChange(null, null,
                    new BigDecimal("35000"), null));
            assertThat(guardada.getValue().generatedCount()).isEqualTo(3);
        }

        @Test
        void cambiarLaCuentaYLaDescripcionLasValidaYRecortaLaDescripcion() {
            guardadaEs(unaSerie().build());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.ALL)
                            .accountId(TransactionMother.DESTINO_ID.toString()).description("  Netflix premium  ")
                            .build()))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(series).update(guardada.capture(), eq(GroupScope.ALL), eq(AHORA), cambios.capture(), isNull());
            assertThat(cambios.getValue()).isEqualTo(new RecurrenceTemplateChange(TransactionMother.DESTINO_ID,
                    null, null, "Netflix premium"));
            assertThat(guardada.getValue().accountId()).isEqualTo(TransactionMother.DESTINO_ID);
        }

        /** FA-123: la moneda de la serie no cambia, asi que la cuenta nueva tiene que estar en ella. */
        @Test
        void unaCuentaEnUsdParaUnaSerieEnCopEsErrorSobreAccountIdYNoGuarda() {
            guardadaEs(unaSerie().build());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.ALL)
                            .accountId(TransactionMother.USD_ID.toString()).build()))
                    .verifyErrorSatisfies(e -> assertThat(((BadRequestException) e).getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField, ErrorDetail::getDescription)
                            .containsExactly(tuple("accountId",
                                    "La cuenta tiene que estar en COP, la moneda de la serie")));

            verify(series, never()).update(any(), any(), any(), any(), any());
        }

        @Test
        void unaCuentaEnCopParaUnaSerieEnUsdEsErrorSobreAccountIdYNoGuarda() {
            guardadaEs(unaSerie().moneda("USD").build());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.ALL)
                            .accountId(TransactionMother.DESTINO_ID.toString()).build()))
                    .verifyErrorSatisfies(e -> assertThat(((BadRequestException) e).getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField, ErrorDetail::getDescription)
                            .containsExactly(tuple("accountId",
                                    "La cuenta tiene que estar en USD, la moneda de la serie")));

            verify(series, never()).update(any(), any(), any(), any(), any());
        }

        @Test
        void unaCuentaEnUsdParaUnaSerieEnUsdSeAplicaYConservaLaMoneda() {
            guardadaEs(unaSerie().moneda("USD").build());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.ALL)
                            .accountId(TransactionMother.USD_ID.toString()).build()))
                    .assertNext(vista -> assertThat(vista.recurrence().currencyCode()).isEqualTo("USD"))
                    .verifyComplete();

            verify(series).update(guardada.capture(), eq(GroupScope.ALL), eq(AHORA), cambios.capture(), isNull());
            assertThat(guardada.getValue().accountId()).isEqualTo(TransactionMother.USD_ID);
            assertThat(cambios.getValue().accountId()).isEqualTo(TransactionMother.USD_ID);
        }

        @Test
        void unaCuentaAjenaEsErrorSobreAccountIdYNoGuarda() {
            guardadaEs(unaSerie().build());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.ALL)
                            .accountId(TransactionMother.AJENA_ID.toString()).build()))
                    .verifyErrorSatisfies(e -> assertThat(campos(e)).containsExactly("accountId"));

            verify(series, never()).update(any(), any(), any(), any(), any());
        }

        @Test
        void unaCategoriaQueNoAplicaAlTipoDeLaSerieEsErrorSobreCategoryId() {
            guardadaEs(unaSerie().build());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.ALL)
                            .categoryId(TransactionMother.SALARIO_ID.toString()).build()))
                    .verifyErrorSatisfies(e -> assertThat(campos(e)).containsExactly("categoryId"));
        }

        @Test
        void cambiarLaPeriodicidadRehaceLasFuturasDesdeMananaConservandoElTotal() {
            guardadaEs(unaSerie().mensual(1, 1, LocalDate.of(2026, 9, 1)).veces(6).creadas(6).build());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.FUTURE)
                            .frequency(Frequency.WEEKLY).dayOfWeek(DayOfWeek.FRIDAY).build()))
                    .assertNext(vista -> assertThat(vista.nextOccurrenceAt())
                            .isEqualTo(Instant.parse("2026-10-16T05:00:00Z")))
                    .verifyComplete();

            verify(series).update(guardada.capture(), eq(GroupScope.FUTURE), eq(AHORA), any(), rehechas.capture());
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

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.FUTURE)
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

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.ALL)
                            .frequency(Frequency.MONTHLY).dayOfMonth(15).interval(1).build()))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(series).update(any(), any(), any(), any(), isNull());
        }

        @Test
        void pasarAMensualSinDiaDelMesEsErrorSobreDayOfMonth() {
            guardadaEs(unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY).build());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.FUTURE)
                            .frequency(Frequency.MONTHLY).build()))
                    .verifyErrorSatisfies(e -> assertThat(campos(e)).containsExactly("dayOfMonth"));
        }

        @Test
        void unDiaDeLaSemanaEnUnaSerieQueQuedaMensualEsErrorSobreDayOfWeek() {
            guardadaEs(unaSerie().build());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.FUTURE)
                            .dayOfWeek(DayOfWeek.MONDAY).build()))
                    .verifyErrorSatisfies(e -> assertThat(campos(e)).containsExactly("dayOfWeek"));
        }

        @Test
        void elIntervaloSeValidaContraLaFrecuenciaResultante() {
            guardadaEs(unaSerie().semanal(20, DayOfWeek.FRIDAY, HOY).build());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.FUTURE)
                            .frequency(Frequency.MONTHLY).dayOfMonth(5).build()))
                    .verifyErrorSatisfies(e -> assertThat(campos(e)).containsExactly("interval"));
        }

        @Test
        void siLaSerieDejoDeEstarActivaAlGuardarEs404() {
            guardadaEs(unaSerie().build());
            when(series.update(any(), any(), any(), any(), any())).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.ALL).amount("1").build()))
                    .verifyErrorSatisfies(e -> assertThat(((BadRequestException) e).getHttpStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        void unaSinFinSinOcurrenciaFuturaExistenteDevuelveLaQueVaACrear() {
            guardadaEs(unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY.minusDays(7)).creadas(3).build());
            when(series.findNextOccurrence(SERIE_ID)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.ALL).amount("1").build()))
                    .assertNext(vista -> assertThat(vista.nextOccurrenceAt())
                            .isEqualTo(Instant.parse("2026-10-23T05:00:00Z")))
                    .verifyComplete();
        }

        @Test
        void unaConFinSinOcurrenciaFuturaNoTieneProxima() {
            guardadaEs(unaSerie().veces(1).creadas(1).build());
            when(series.findNextOccurrence(SERIE_ID)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().update(USER_ID, SERIE_ID, unParche(GroupScope.ALL).amount("1").build()))
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

    @Nested
    class Cancelar {

        @Test
        void cancelaConElInstanteDelReloj() {
            when(series.cancel(SERIE_ID, USER_ID, AHORA)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().cancel(USER_ID, SERIE_ID)).verifyComplete();

            verify(series).cancel(SERIE_ID, USER_ID, AHORA);
        }

        @Test
        void sinUnaSerieActivaQueCancelarEs404SobreId() {
            when(series.cancel(SERIE_ID, USER_ID, AHORA)).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().cancel(USER_ID, SERIE_ID))
                    .verifyErrorSatisfies(e -> {
                        BadRequestException error = (BadRequestException) e;
                        assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(error.getErrorResponse().getErrors()).extracting(ErrorDetail::getField)
                                .containsExactly("id");
                    });
        }
    }

    private RecurrenceUseCase useCase() {
        return new RecurrenceUseCase(cuentas, categorias, monedas, usuarios, series, alDia,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Mono<UserProfile> perfilEn(String zona) {
        return Mono.just(new UserProfile(USER_ID, "ana@ejemplo.com", "Ana", null, null, "COP", zona, true, false,
                false));
    }
}
