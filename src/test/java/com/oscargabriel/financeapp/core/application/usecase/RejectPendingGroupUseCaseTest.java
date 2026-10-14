package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.application.usecase.ApprovePendingTransactionUseCaseTest.esError;
import static com.oscargabriel.financeapp.core.support.TransactionMother.TRANSFERENCIA_GUARDADA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RejectPendingGroupUseCaseTest {

    private static final UUID GRUPO_ID = UUID.fromString("71000000-0000-7000-8000-000000000001");

    @Mock
    private TransactionRepositoryPort repositorio;

    private RejectPendingGroupUseCase casoDeUso;

    @BeforeEach
    void casoDeUso() {
        casoDeUso = new RejectPendingGroupUseCase(repositorio);
    }

    @Test
    void borraSoloLosQueSiguenPendientes() {
        Transaction yaAprobado = TransactionMother.enGrupo(TransactionMother.unGastoGuardado(), GRUPO_ID);
        when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(yaAprobado, transferenciaPendiente()));
        when(repositorio.deletePendingIn(List.of(TRANSFERENCIA_GUARDADA_ID), USER_ID)).thenReturn(Mono.just(1L));

        StepVerifier.create(casoDeUso.reject(USER_ID, GRUPO_ID)).verifyComplete();

        verify(repositorio).deletePendingIn(List.of(TRANSFERENCIA_GUARDADA_ID), USER_ID);
    }

    @Test
    void unGrupoSinMovimientosDelUsuarioEsNoEncontradoSobreElGrupo() {
        when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.empty());

        StepVerifier.create(casoDeUso.reject(USER_ID, GRUPO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "groupId", ErrorCodes.NOT_FOUND))
                .verify();

        verify(repositorio, never()).deletePendingIn(any(), any());
    }

    @Test
    void unGrupoYaConfirmadoEsUnConflictoSobreElEstadoSinBorrarNada() {
        when(repositorio.findByGroup(GRUPO_ID, USER_ID))
                .thenReturn(Flux.just(TransactionMother.enGrupo(TransactionMother.unGastoGuardado(), GRUPO_ID)));

        StepVerifier.create(casoDeUso.reject(USER_ID, GRUPO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "status", ErrorCodes.INVALID_STATE))
                .verify();

        verify(repositorio, never()).deletePendingIn(any(), any());
    }

    @Test
    void siOtroRequestLosResolvioEnMedioEsNoEncontrado() {
        when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(transferenciaPendiente()));
        when(repositorio.deletePendingIn(List.of(TRANSFERENCIA_GUARDADA_ID), USER_ID)).thenReturn(Mono.just(0L));

        StepVerifier.create(casoDeUso.reject(USER_ID, GRUPO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "groupId", ErrorCodes.NOT_FOUND))
                .verify();
    }

    private static Transaction transferenciaPendiente() {
        return TransactionMother.enGrupo(TransactionMother.unaTransferenciaPendienteEntre(TransactionMother.ORIGEN_ID,
                TransactionMother.DESTINO_ID), GRUPO_ID);
    }
}
