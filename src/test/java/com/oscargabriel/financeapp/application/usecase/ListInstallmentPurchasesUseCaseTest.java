package com.oscargabriel.financeapp.application.usecase;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.domain.port.out.InstallmentPurchaseRepositoryPort;
import com.oscargabriel.financeapp.support.InstallmentMother;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ListInstallmentPurchasesUseCaseTest {

    @Mock
    private InstallmentPurchaseRepositoryPort compras;

    @InjectMocks
    private ListInstallmentPurchasesUseCase casoDeUso;

    @Test
    void devuelveLasActivasDelUsuarioEnElOrdenDelRepositorio() {
        when(compras.findActiveByUser(TransactionMother.USER_ID))
                .thenReturn(Flux.just(InstallmentMother.televisorSinPagar()));

        StepVerifier.create(casoDeUso.list(TransactionMother.USER_ID))
                .expectNext(InstallmentMother.televisorSinPagar())
                .verifyComplete();
    }
}