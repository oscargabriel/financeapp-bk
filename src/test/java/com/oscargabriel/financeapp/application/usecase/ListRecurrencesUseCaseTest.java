package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.RecurrenceMother.HOY;
import static com.oscargabriel.financeapp.support.RecurrenceMother.unaSerie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.oscargabriel.financeapp.domain.model.RecurrenceView;
import com.oscargabriel.financeapp.domain.model.UserProfile;
import com.oscargabriel.financeapp.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListRecurrencesUseCaseTest {

    private static final UUID USER_ID = TransactionMother.USER_ID;

    @Mock
    private RecurrenceRepositoryPort series;

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private SeriesAlDia alDia;

    private ListRecurrencesUseCase casoDeUso;

    @BeforeEach
    void escenario() {
        when(alDia.ponerAlDia(USER_ID)).thenReturn(Mono.empty());
        when(usuarios.findActiveProfile(USER_ID)).thenReturn(Mono.just(
                new UserProfile(USER_ID, "ana@ejemplo.com", "Ana", null, null, "COP", "America/Bogota")));
        casoDeUso = new ListRecurrencesUseCase(series, usuarios, alDia);
    }

    @Test
    void devuelveLasActivasDelRepositorioSinLeerElPerfil() {
        RecurrenceView primera = new RecurrenceView(unaSerie().id(UUID.randomUUID()).build(),
                Instant.parse("2026-10-12T05:00:00Z"));
        RecurrenceView segunda = new RecurrenceView(unaSerie().id(UUID.randomUUID()).build(),
                Instant.parse("2026-10-15T05:00:00Z"));
        when(series.findActiveByUser(USER_ID)).thenReturn(Flux.just(primera, segunda));

        StepVerifier.create(casoDeUso.list(USER_ID))
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

        StepVerifier.create(casoDeUso.list(USER_ID).map(RecurrenceView::nextOccurrenceAt))
                .expectNext(Instant.parse("2026-10-15T05:00:00Z"), Instant.parse("2026-10-23T05:00:00Z"),
                        Instant.parse("2026-11-15T05:00:00Z"))
                .verifyComplete();
    }

    @Test
    void poneAlDiaLasSeriesAntesDeListar() {
        when(alDia.ponerAlDia(USER_ID)).thenReturn(Mono.error(new IllegalStateException("sin base")));

        StepVerifier.create(casoDeUso.list(USER_ID)).verifyError(IllegalStateException.class);

        verifyNoInteractions(series);
    }
}
