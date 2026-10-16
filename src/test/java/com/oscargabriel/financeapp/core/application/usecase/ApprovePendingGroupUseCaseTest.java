package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.application.usecase.ApprovePendingTransactionUseCaseTest.esError;
import static com.oscargabriel.financeapp.core.support.TransactionMother.GASTO_GUARDADO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.TRANSFERENCIA_GUARDADA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.support.RelojFijo;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ApprovePendingGroupUseCaseTest {

    private static final UUID GRUPO_ID = UUID.fromString("71000000-0000-7000-8000-000000000001");

    @Mock
    private TransactionRepositoryPort repositorio;

    @Mock
    private AccountRepositoryPort cuentas;

    @Mock
    private CategoryRepositoryPort categorias;

    private ApprovePendingGroupUseCase casoDeUso;

    /** Las cuentas y las categorias del usuario de TransactionMother, por id. */
    @BeforeEach
    void escenario() {
        casoDeUso = new ApprovePendingGroupUseCase(repositorio, cuentas, categorias,
                Clock.fixed(RelojFijo.AHORA, ZoneOffset.UTC));
        for (Account cuenta : TransactionMother.cuentasDelUsuario()) {
            lenient().when(cuentas.findActiveByIdAndUser(cuenta.id(), USER_ID)).thenReturn(Mono.just(cuenta));
        }
        lenient().when(categorias.findActiveByIdAndUser(TransactionMother.MERCADO_ID, USER_ID))
                .thenReturn(Mono.just(TransactionMother.categoriasDelUsuario().getFirst()));
    }

    @Test
    void confirmaTodosLosPendientesYLosDevuelveConfirmadosEnElOrdenDelGrupo() {
        Transaction gasto = pendiente(TransactionMother.unGastoPendiente());
        Transaction transferencia = transferenciaPendiente();
        when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(gasto, transferencia));
        when(repositorio.confirmAll(List.of(GASTO_GUARDADO_ID, TRANSFERENCIA_GUARDADA_ID), USER_ID))
                .thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.approve(USER_ID, GRUPO_ID))
                .assertNext(t -> assertThat(t).isEqualTo(TransactionMother.conEstado(gasto, TransactionStatus.CONFIRMED)))
                .assertNext(t -> assertThat(t).isEqualTo(
                        TransactionMother.conEstado(transferencia, TransactionStatus.CONFIRMED)))
                .verifyComplete();
    }

    /** FIN-101 */
    @Test
    void aprobarUnGrupoQueLlegoPorCorreoConservaElOrigenYElGrupo() {
        Transaction gasto = pendiente(
                TransactionMother.conOrigen(TransactionMother.unGastoPendiente(), TransactionOrigin.EMAIL));
        when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(gasto));
        when(repositorio.confirmAll(List.of(GASTO_GUARDADO_ID), USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.approve(USER_ID, GRUPO_ID))
                .assertNext(t -> assertThat(t).extracting(Transaction::status, Transaction::origin, Transaction::groupId)
                        .containsExactly(TransactionStatus.CONFIRMED, TransactionOrigin.EMAIL, GRUPO_ID))
                .verifyComplete();
    }

    @Test
    void soloConfirmaLosQueSiguenPendientes() {
        Transaction yaAprobado = TransactionMother.enGrupo(TransactionMother.unGastoGuardado(), GRUPO_ID);
        Transaction transferencia = transferenciaPendiente();
        when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(yaAprobado, transferencia));
        when(repositorio.confirmAll(List.of(TRANSFERENCIA_GUARDADA_ID), USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.approve(USER_ID, GRUPO_ID))
                .assertNext(t -> assertThat(t.id()).isEqualTo(TRANSFERENCIA_GUARDADA_ID))
                .verifyComplete();
    }

    @Test
    void unGrupoSinMovimientosDelUsuarioEsNoEncontradoSobreElGrupo() {
        when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.empty());

        StepVerifier.create(casoDeUso.approve(USER_ID, GRUPO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "groupId", ErrorCodes.NOT_FOUND))
                .verify();

        verify(repositorio, never()).confirmAll(any(), any());
    }

    @Test
    void unGrupoYaConfirmadoEsUnConflictoSobreElEstado() {
        when(repositorio.findByGroup(GRUPO_ID, USER_ID))
                .thenReturn(Flux.just(TransactionMother.enGrupo(TransactionMother.unGastoGuardado(), GRUPO_ID)));

        StepVerifier.create(casoDeUso.approve(USER_ID, GRUPO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "status", ErrorCodes.INVALID_STATE))
                .verify();

        verify(repositorio, never()).confirmAll(any(), any());
    }

    @Test
    void siUnoNoSePuedeAprobarNoSeConfirmaNinguno() {
        Transaction gasto = pendiente(TransactionMother.unGastoPendiente());
        Transaction enInactiva = pendiente(TransactionMother.en(TransactionMother.unaTransferenciaGuardada(),
                TransactionMother.INACTIVA_ID, TransactionMother.DESTINO_ID, "1000"));
        when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(gasto, enInactiva));

        StepVerifier.create(casoDeUso.approve(USER_ID, GRUPO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "[1].accountId",
                        ErrorCodes.INVALID_STATE))
                .verify();

        verify(repositorio, never()).confirmAll(any(), any());
    }

    @Test
    void siOtroRequestCambioElGrupoEnMedioEsNoEncontrado() {
        Transaction gasto = pendiente(TransactionMother.unGastoPendiente());
        when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(gasto));
        when(repositorio.confirmAll(List.of(GASTO_GUARDADO_ID), USER_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.approve(USER_ID, GRUPO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "groupId", ErrorCodes.NOT_FOUND))
                .verify();
    }

    private static Transaction pendiente(Transaction t) {
        return TransactionMother.enGrupo(TransactionMother.conEstado(t, TransactionStatus.PENDING), GRUPO_ID);
    }

    private static Transaction transferenciaPendiente() {
        return TransactionMother.enGrupo(TransactionMother.unaTransferenciaPendienteEntre(TransactionMother.ORIGEN_ID,
                TransactionMother.DESTINO_ID), GRUPO_ID);
    }
}
