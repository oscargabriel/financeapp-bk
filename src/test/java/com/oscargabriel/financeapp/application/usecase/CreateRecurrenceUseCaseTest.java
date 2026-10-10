package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.RecurrenceMother.HOY;
import static com.oscargabriel.financeapp.support.RecurrenceMother.unAlta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
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
import com.oscargabriel.financeapp.domain.model.CreateRecurrenceCommand;
import com.oscargabriel.financeapp.domain.model.Recurrence;
import com.oscargabriel.financeapp.domain.model.RecurrenceStatus;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;
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
class CreateRecurrenceUseCaseTest {

    /** Viernes 9 de octubre a las 10:00 en Bogota. */
    private static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");

    private static final UUID USER_ID = TransactionMother.USER_ID;

    @Mock
    private AccountQueryPort cuentas;

    @Mock
    private CategoryQueryPort categorias;

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private RecurrenceRepositoryPort series;

    @Captor
    private ArgumentCaptor<Recurrence> serieGuardada;

    @Captor
    private ArgumentCaptor<List<Transaction>> ocurrencias;

    private CreateRecurrenceUseCase casoDeUso;

    @BeforeEach
    void escenario() {
        when(cuentas.findByUser(USER_ID, true)).thenReturn(Flux.fromIterable(TransactionMother.cuentasDelUsuario()));
        when(categorias.findActiveByUser(eq(USER_ID), any()))
                .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
        enZona("America/Bogota");
        when(series.save(any(), anyList())).thenReturn(Mono.empty());
        casoDeUso = new CreateRecurrenceUseCase(cuentas, categorias, usuarios, series, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void guardaLaSerieConSusOcurrenciasYDevuelveLaProxima() {
        StepVerifier.create(casoDeUso.create(USER_ID, unAlta().build()))
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
        enZona("Asia/Tokyo");

        StepVerifier.create(casoDeUso.create(USER_ID, unAlta().veces(1).build()))
                .assertNext(vista -> assertThat(vista.nextOccurrenceAt())
                        .isEqualTo(Instant.parse("2026-10-14T15:00:00Z")))
                .verifyComplete();
    }

    @Test
    void unaSinFinQueEmpezoHaceTresSemanasCreaHastaHoyMasLaSiguiente() {
        CreateRecurrenceCommand alta = unAlta().semanal(DayOfWeek.FRIDAY).desde(HOY.minusDays(21)).sinFin().build();

        StepVerifier.create(casoDeUso.create(USER_ID, alta))
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

        StepVerifier.create(casoDeUso.create(USER_ID, alta))
                .assertNext(vista -> assertThat(vista.nextOccurrenceAt()).isNull())
                .verifyComplete();
    }

    @Test
    void unaCuentaAjenaEsErrorSobreAccountIdYNoGuardaNada() {
        CreateRecurrenceCommand alta = unAlta().accountId(TransactionMother.AJENA_ID.toString()).build();

        StepVerifier.create(casoDeUso.create(USER_ID, alta))
                .verifyErrorSatisfies(e -> assertThat(errores(e))
                        .extracting(ErrorDetail::getField, ErrorDetail::getDescription)
                        .containsExactly(tuple("accountId", "La cuenta no existe")));

        verify(series, never()).save(any(), anyList());
    }

    /** Los movimientos sueltos admiten cualquier moneda desde FA-51; las series siguen en COP. */
    @Test
    void unaCuentaEnUsdEsErrorSobreAccountId() {
        CreateRecurrenceCommand alta = unAlta().accountId(TransactionMother.USD_ID.toString()).build();

        StepVerifier.create(casoDeUso.create(USER_ID, alta))
                .verifyErrorSatisfies(e -> assertThat(errores(e))
                        .extracting(ErrorDetail::getField, ErrorDetail::getDescription)
                        .containsExactly(tuple("accountId", "Por ahora las series solo admiten cuentas en COP")));

        verify(series, never()).save(any(), anyList());
    }

    @Test
    void unaCategoriaQueNoAplicaAlTipoEsErrorSobreCategoryId() {
        CreateRecurrenceCommand alta = unAlta().categoryId(TransactionMother.SALARIO_ID.toString()).build();

        StepVerifier.create(casoDeUso.create(USER_ID, alta))
                .verifyErrorSatisfies(e -> assertThat(errores(e)).extracting(ErrorDetail::getField)
                        .containsExactly("categoryId"));
    }

    @Test
    void masDeQuinientasOcurrenciasConFinEsErrorSobreEndDate() {
        CreateRecurrenceCommand alta = unAlta().semanal(DayOfWeek.MONDAY).hasta(HOY.plusYears(10)).build();

        StepVerifier.create(casoDeUso.create(USER_ID, alta))
                .verifyErrorSatisfies(e -> assertThat(errores(e)).extracting(ErrorDetail::getField)
                        .containsExactly("endDate"));

        verify(series, never()).save(any(), anyList());
    }

    @Test
    void unaSinFinQueEmpiezaDemasiadoAtrasEsErrorSobreStartDate() {
        CreateRecurrenceCommand alta = unAlta().semanal(DayOfWeek.MONDAY).desde(HOY.minusYears(10)).sinFin().build();

        StepVerifier.create(casoDeUso.create(USER_ID, alta))
                .verifyErrorSatisfies(e -> assertThat(errores(e)).extracting(ErrorDetail::getField)
                        .containsExactly("startDate"));
    }

    @Test
    void unaSerieSinNingunaOcurrenciaAntesDeSuFinEsErrorSobreEndDate() {
        CreateRecurrenceCommand alta = unAlta().semanal(DayOfWeek.MONDAY).desde(HOY).hasta(HOY.plusDays(1)).build();

        StepVerifier.create(casoDeUso.create(USER_ID, alta))
                .verifyErrorSatisfies(e -> assertThat(errores(e)).extracting(ErrorDetail::getField)
                        .containsExactly("endDate"));
    }

    private void enZona(String zona) {
        when(usuarios.findActiveProfile(USER_ID))
                .thenReturn(Mono.just(new UserProfile(USER_ID, "ana@ejemplo.com", "Ana", null, null, "COP", zona)));
    }

    private static List<ErrorDetail> errores(Throwable e) {
        assertThat(e).isInstanceOf(BadRequestException.class);
        BadRequestException error = (BadRequestException) e;
        assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        return error.getErrorResponse().getErrors();
    }
}
