package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.RecurrenceMother.HOY;
import static com.oscargabriel.financeapp.support.RecurrenceMother.unaSerie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import com.oscargabriel.financeapp.domain.model.Recurrence;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.UserProfile;
import com.oscargabriel.financeapp.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeriesAlDiaTest {

    private static final UUID USER_ID = TransactionMother.USER_ID;

    /** Viernes 9 de octubre a las 10:00 en Bogota. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-09T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RecurrenceRepositoryPort series;

    @Mock
    private UserRepositoryPort usuarios;

    @Captor
    private ArgumentCaptor<Recurrence> serieAlDia;

    @Captor
    private ArgumentCaptor<List<Transaction>> nuevas;

    private SeriesAlDia alDia;

    @BeforeEach
    void escenario() {
        when(usuarios.findActiveProfile(USER_ID))
                .thenReturn(Mono.just(new UserProfile(USER_ID, "ana@ejemplo.com", "Ana", null, null, "COP",
                        "America/Bogota")));
        when(series.append(any(), anyInt(), anyList())).thenReturn(Mono.just(true));
        alDia = new SeriesAlDia(series, usuarios, RELOJ);
    }

    @Test
    void sinSeriesSinFinNoLeeElPerfilNiEscribe() {
        when(series.findOpenActiveByUser(USER_ID)).thenReturn(Flux.empty());

        StepVerifier.create(alDia.ponerAlDia(USER_ID)).verifyComplete();

        verifyNoInteractions(usuarios);
        verify(series, never()).append(any(), anyInt(), anyList());
    }

    @Test
    void agregaLasQueFaltanDesdeElContadorConElContadorQueLeyo() {
        Recurrence atrasada = unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY.minusDays(28)).creadas(2).build();
        when(series.findOpenActiveByUser(USER_ID)).thenReturn(Flux.just(atrasada));

        StepVerifier.create(alDia.ponerAlDia(USER_ID)).verifyComplete();

        verify(series).append(serieAlDia.capture(), eq(2), nuevas.capture());
        assertThat(serieAlDia.getValue().generatedCount()).isEqualTo(6);
        assertThat(nuevas.getValue()).extracting(Transaction::occurredAt).containsExactly(
                Instant.parse("2026-09-25T05:00:00Z"), Instant.parse("2026-10-02T05:00:00Z"),
                Instant.parse("2026-10-09T05:00:00Z"), Instant.parse("2026-10-16T05:00:00Z"));
        assertThat(nuevas.getValue()).allSatisfy(t -> assertThat(t.recurrenceId()).isEqualTo(atrasada.id()));
    }

    @Test
    void unaSerieYaAlDiaNoEscribe() {
        Recurrence alDiaYa = unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY.minusDays(7)).creadas(3).build();
        when(series.findOpenActiveByUser(USER_ID)).thenReturn(Flux.just(alDiaYa));

        StepVerifier.create(alDia.ponerAlDia(USER_ID)).verifyComplete();

        verify(series, never()).append(any(), anyInt(), anyList());
    }

    @Test
    void siOtraPeticionYaLaPusoAlDiaSigueSinError() {
        Recurrence atrasada = unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY.minusDays(28)).creadas(2).build();
        when(series.findOpenActiveByUser(USER_ID)).thenReturn(Flux.just(atrasada));
        when(series.append(any(), anyInt(), anyList())).thenReturn(Mono.just(false));

        StepVerifier.create(alDia.ponerAlDia(USER_ID)).verifyComplete();
    }

    @Test
    void unaSerieQueLlevaAniosSinLeerseAvanzaComoMuchoElTopePorLectura() {
        Recurrence olvidada = unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY.minusYears(12)).creadas(1).build();
        when(series.findOpenActiveByUser(USER_ID)).thenReturn(Flux.just(olvidada));

        StepVerifier.create(alDia.ponerAlDia(USER_ID)).verifyComplete();

        verify(series).append(serieAlDia.capture(), eq(1), nuevas.capture());
        assertThat(nuevas.getValue()).hasSize(Recurrence.TOPE);
        assertThat(serieAlDia.getValue().generatedCount()).isEqualTo(1 + Recurrence.TOPE);
    }
}
