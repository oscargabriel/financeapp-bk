package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.TransactionMother.GASTO_GUARDADO_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ApprovePendingTransactionUseCaseTest {

    @Mock
    private TransactionRepositoryPort repositorio;

    @InjectMocks
    private ApprovePendingTransactionUseCase casoDeUso;

    @Test
    void confirmaElPendienteYLoDevuelveConfirmado() {
        Transaction pendiente = TransactionMother.unGastoPendiente();
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
        when(repositorio.confirm(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID))
                .assertNext(t -> assertThat(t).isEqualTo(
                        TransactionMother.conEstado(pendiente, TransactionStatus.CONFIRMED)))
                .verifyComplete();

        verify(repositorio).confirm(GASTO_GUARDADO_ID, USER_ID);
    }

    @Test
    void unMovimientoInexistenteOAjenoEsNoEncontradoSinTocarNada() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                .verify();

        verify(repositorio, never()).confirm(any(), any());
    }

    @Test
    void unMovimientoYaConfirmadoEsUnConflictoSobreElEstado() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(TransactionMother.unGastoGuardado()));

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "status", ErrorCodes.INVALID_STATE))
                .verify();

        verify(repositorio, never()).confirm(any(), any());
    }

    @Test
    void siOtroRequestLoConfirmoOBorroEnMedioEsNoEncontrado() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(TransactionMother.unGastoPendiente()));
        when(repositorio.confirm(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                .verify();
    }

    static void esError(Throwable error, HttpStatus status, String campo, ErrorCodes codigo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors())
                .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                .containsExactly(tuple(campo, codigo.getCode()));
    }
}
