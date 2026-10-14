package com.oscargabriel.financeapp.core.application.usecase;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.core.domain.model.Sharing;
import com.oscargabriel.financeapp.core.domain.port.out.SharingQueryPort;
import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DescribeSharingUseCaseTest {

    @Mock
    private SharingQueryPort consultas;

    @InjectMocks
    private DescribeSharingUseCase useCase;

    @Test
    void devuelveLoCompartidoDeLosMovimientosDelUsuario() {
        Map<UUID, Sharing> compartido = Map.of(TransactionMother.GASTO_GUARDADO_ID, new Sharing(
                new Sharing.Person("ana@correo.com", "Ana", null),
                new Sharing.Source("Cena", new BigDecimal("90000"), "COP"), null));
        when(consultas.findFor(TransactionMother.USER_ID, List.of(TransactionMother.GASTO_GUARDADO_ID)))
                .thenReturn(Mono.just(compartido));

        StepVerifier.create(useCase.describe(TransactionMother.USER_ID, List.of(TransactionMother.GASTO_GUARDADO_ID)))
                .expectNext(compartido)
                .verifyComplete();
    }

    /** Un alta, una lista o un reporte vacios no consultan la base. */
    @Test
    void sinMovimientosNoConsulta() {
        StepVerifier.create(useCase.describe(TransactionMother.USER_ID, List.of()))
                .expectNext(Map.of())
                .verifyComplete();

        verifyNoInteractions(consultas);
    }
}
