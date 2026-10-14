package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.application.usecase.ApprovePendingTransactionUseCaseTest.esError;
import static com.oscargabriel.financeapp.core.support.TransactionMother.GASTO_GUARDADO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.INGRESO_GUARDADO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.TRANSFERENCIA_GUARDADA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
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

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RejectPendingBatchUseCaseTest {

    private static final UUID AJENO_ID = UUID.fromString("50000000-0000-7000-8000-0000000000ff");

    @Mock
    private TransactionRepositoryPort repositorio;

    private RejectPendingBatchUseCase casoDeUso;

    @BeforeEach
    void casoDeUso() {
        casoDeUso = new RejectPendingBatchUseCase(repositorio);
    }

    @Test
    void rechazaTodosLosPendientesDelLote() {
        List<UUID> ids = List.of(GASTO_GUARDADO_ID, TRANSFERENCIA_GUARDADA_ID);
        when(repositorio.findByIdsAndUser(ids, USER_ID)).thenReturn(Flux.just(transferenciaPendiente(), gastoPendiente()));
        when(repositorio.deleteAllPending(ids, USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.reject(USER_ID, ids)).verifyComplete();

        verify(repositorio).deleteAllPending(ids, USER_ID);
    }

    @Test
    void unIdQueNoEsDelUsuarioEsNoEncontradoEnSuPosicionSinRechazarNinguno() {
        List<UUID> ids = List.of(GASTO_GUARDADO_ID, AJENO_ID);
        when(repositorio.findByIdsAndUser(ids, USER_ID)).thenReturn(Flux.just(gastoPendiente()));

        StepVerifier.create(casoDeUso.reject(USER_ID, ids))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "ids[1]", ErrorCodes.NOT_FOUND))
                .verify();

        verify(repositorio, never()).deleteAllPending(any(), any());
    }

    @Test
    void unMovimientoConfirmadoEsUnConflictoEnSuPosicionSinRechazarNinguno() {
        List<UUID> ids = List.of(GASTO_GUARDADO_ID, INGRESO_GUARDADO_ID);
        when(repositorio.findByIdsAndUser(ids, USER_ID))
                .thenReturn(Flux.just(TransactionMother.unIngresoGuardado(), gastoPendiente()));

        StepVerifier.create(casoDeUso.reject(USER_ID, ids))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "ids[1]", ErrorCodes.INVALID_STATE))
                .verify();

        verify(repositorio, never()).deleteAllPending(any(), any());
    }

    @Test
    void conNoEncontradosYConfirmadosSaleSoloElNoEncontrado() {
        List<UUID> ids = List.of(AJENO_ID, INGRESO_GUARDADO_ID);
        when(repositorio.findByIdsAndUser(ids, USER_ID)).thenReturn(Flux.just(TransactionMother.unIngresoGuardado()));

        StepVerifier.create(casoDeUso.reject(USER_ID, ids))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "ids[0]", ErrorCodes.NOT_FOUND))
                .verify();
    }

    @Test
    void cadaIdAusenteLlevaSuPropioError() {
        List<UUID> ids = List.of(GASTO_GUARDADO_ID, TRANSFERENCIA_GUARDADA_ID, AJENO_ID);
        when(repositorio.findByIdsAndUser(ids, USER_ID)).thenReturn(Flux.empty());

        StepVerifier.create(casoDeUso.reject(USER_ID, ids))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                            .containsExactly(tuple("ids[0]", "NOT_FOUND"), tuple("ids[1]", "NOT_FOUND"),
                                    tuple("ids[2]", "NOT_FOUND"));
                })
                .verify();
    }

    @Test
    void siOtroRequestResolvioAlgunoEnMedioEsNoEncontradoSobreElLote() {
        List<UUID> ids = List.of(GASTO_GUARDADO_ID);
        when(repositorio.findByIdsAndUser(ids, USER_ID)).thenReturn(Flux.just(gastoPendiente()));
        when(repositorio.deleteAllPending(ids, USER_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.reject(USER_ID, ids))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "ids", ErrorCodes.NOT_FOUND))
                .verify();
    }

    private static Transaction gastoPendiente() {
        return TransactionMother.unGastoPendiente();
    }

    private static Transaction transferenciaPendiente() {
        return TransactionMother.unaTransferenciaPendienteEntre(TransactionMother.ORIGEN_ID,
                TransactionMother.DESTINO_ID);
    }
}
