package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.AffiliationMother.DESTINATARIO_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.unaVista;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliationRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ListAffiliationsUseCaseTest {

    @Mock
    private AffiliationRepositoryPort afiliaciones;

    @InjectMocks
    private ListAffiliationsUseCase casoDeUso;

    @Test
    void devuelveLasAbiertasDelUsuarioEnElOrdenDelRepositorio() {
        AffiliationView recibida = unaVista().build();
        AffiliationView aceptada = unaVista().enviada().conEstado(AffiliationStatus.ACCEPTED).build();
        when(afiliaciones.findOpenByUser(DESTINATARIO_ID)).thenReturn(Flux.just(recibida, aceptada));

        StepVerifier.create(casoDeUso.list(DESTINATARIO_ID))
                .expectNext(recibida, aceptada)
                .verifyComplete();
    }
}
