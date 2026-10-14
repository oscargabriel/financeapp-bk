package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.TransactionMother.USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ListPendingTransactionsUseCaseTest {

    @Mock
    private TransactionRepositoryPort repositorio;

    @InjectMocks
    private ListPendingTransactionsUseCase casoDeUso;

    @Test
    void devuelveLosPendientesDelUsuarioEnElOrdenDelRepositorio() {
        Transaction primero = TransactionMother.unGastoPendiente();
        Transaction segundo = TransactionMother.conEstado(TransactionMother.unaTransferenciaGuardada(),
                primero.status());
        when(repositorio.findPendingByUser(USER_ID)).thenReturn(Flux.just(primero, segundo));

        StepVerifier.create(casoDeUso.listPending(USER_ID, Optional.empty()))
                .expectNext(primero, segundo)
                .verifyComplete();

        verify(repositorio, never()).findPendingSharedBy(any(), any());
    }

    @Test
    void conFiltroPideSoloLosDeEsaPersonaConElCorreoNormalizado() {
        Transaction compartido = TransactionMother.unGastoPendiente();
        when(repositorio.findPendingSharedBy(USER_ID, "laura@correo.com")).thenReturn(Flux.just(compartido));

        StepVerifier.create(casoDeUso.listPending(USER_ID, Optional.of("  Laura@Correo.COM ")))
                .expectNext(compartido)
                .verifyComplete();

        verify(repositorio, never()).findPendingByUser(any());
    }
}
