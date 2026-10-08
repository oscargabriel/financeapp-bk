package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.application.usecase.ApprovePendingTransactionUseCaseTest.esError;
import static com.oscargabriel.financeapp.support.TransactionMother.GASTO_GUARDADO_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RejectPendingTransactionUseCaseTest {

    @Mock
    private TransactionRepositoryPort repositorio;

    @InjectMocks
    private RejectPendingTransactionUseCase casoDeUso;

    @Test
    void borraElPendienteYCompletaVacio() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(TransactionMother.unGastoPendiente()));
        when(repositorio.deletePending(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.reject(USER_ID, GASTO_GUARDADO_ID))
                .verifyComplete();

        verify(repositorio).deletePending(GASTO_GUARDADO_ID, USER_ID);
    }

    @Test
    void unMovimientoInexistenteOAjenoEsNoEncontradoSinBorrarNada() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.reject(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                .verify();

        verify(repositorio, never()).deletePending(any(), any());
    }

    @Test
    void unMovimientoYaConfirmadoEsUnConflictoYNoSeBorra() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(TransactionMother.unGastoGuardado()));

        StepVerifier.create(casoDeUso.reject(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "status", ErrorCodes.INVALID_STATE))
                .verify();

        verify(repositorio, never()).deletePending(any(), any());
    }

    @Test
    void siOtroRequestLoAproboOBorroEnMedioEsNoEncontrado() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(TransactionMother.unGastoPendiente()));
        when(repositorio.deletePending(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.reject(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                .verify();
    }
}
