package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.TransactionMother.USER_ID;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.support.TransactionMother;

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

        StepVerifier.create(casoDeUso.listPending(USER_ID))
                .expectNext(primero, segundo)
                .verifyComplete();
    }
}
