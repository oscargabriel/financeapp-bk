package com.oscargabriel.financeapp.app.puente;

import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.core.domain.port.in.CatchUpSeriesPort;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

@ExtendWith(MockitoExtension.class)
class SeriesDelAsistenteAdapterTest {

    private static final UUID USUARIO = UUID.fromString("00000000-0000-7000-8000-000000000001");

    @Mock
    private CatchUpSeriesPort series;

    private SeriesDelAsistenteAdapter puente;

    @BeforeEach
    void escenario() {
        puente = new SeriesDelAsistenteAdapter(series);
    }

    @Test
    void poneAlDiaLasSeriesDelUsuarioEnElCore() {
        PublisherProbe<Void> puesta = PublisherProbe.empty();
        when(series.ponerAlDia(USUARIO)).thenReturn(puesta.mono());

        StepVerifier.create(puente.catchUp(USUARIO)).verifyComplete();

        puesta.assertWasSubscribed();
    }

    @Test
    void propagaElErrorDelCore() {
        IllegalStateException falla = new IllegalStateException("la base no responde");
        when(series.ponerAlDia(USUARIO)).thenReturn(Mono.error(falla));

        StepVerifier.create(puente.catchUp(USUARIO)).verifyErrorMatches(e -> e == falla);
    }
}
