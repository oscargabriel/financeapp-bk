package com.oscargabriel.financeapp.core.application.usecase;

import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

@ExtendWith(MockitoExtension.class)
class CatchUpSeriesUseCaseTest {

    private static final UUID USER_ID = TransactionMother.USER_ID;

    @Mock
    private SeriesAlDia alDia;

    private CatchUpSeriesUseCase casoDeUso;

    @BeforeEach
    void escenario() {
        casoDeUso = new CatchUpSeriesUseCase(alDia);
    }

    @Test
    void poneAlDiaLasSeriesDelUsuario() {
        PublisherProbe<Void> puesta = PublisherProbe.empty();
        when(alDia.ponerAlDia(USER_ID)).thenReturn(puesta.mono());

        StepVerifier.create(casoDeUso.ponerAlDia(USER_ID)).verifyComplete();

        puesta.assertWasSubscribed();
    }

    @Test
    void propagaElErrorDeLaPuestaAlDia() {
        IllegalStateException falla = new IllegalStateException("la base no responde");
        when(alDia.ponerAlDia(USER_ID)).thenReturn(Mono.error(falla));

        StepVerifier.create(casoDeUso.ponerAlDia(USER_ID)).verifyErrorMatches(e -> e == falla);
    }
}
