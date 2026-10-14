package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.TransactionMother.GASTO_GUARDADO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.TRANSFERENCIA_GUARDADA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
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
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.core.support.AccountMother;
import com.oscargabriel.financeapp.compartido.support.RelojFijo;
import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ApprovePendingTransactionUseCaseTest {

    @Mock
    private TransactionRepositoryPort repositorio;

    @Mock
    private AccountRepositoryPort cuentas;

    @Mock
    private CategoryRepositoryPort categorias;

    private ApprovePendingTransactionUseCase casoDeUso;

    /** Mercado viva para todos; cualquier otra categoria, borrada. */
    @BeforeEach
    void reloj() {
        casoDeUso = new ApprovePendingTransactionUseCase(repositorio, cuentas, categorias,
                Clock.fixed(RelojFijo.AHORA, ZoneOffset.UTC));
        lenient().when(categorias.findActiveByIdAndUser(any(), any())).thenReturn(Mono.empty());
        lenient().when(categorias.findActiveByIdAndUser(TransactionMother.MERCADO_ID, USER_ID))
                .thenReturn(Mono.just(TransactionMother.categoriasDelUsuario().getFirst()));
    }

    @Test
    void unGastoConLaCategoriaBorradaEsUnConflictoSobreLaCategoriaSinConfirmar() {
        Transaction pendiente = TransactionMother.conCategoria(TransactionMother.unGastoPendiente(), UUID.randomUUID());
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
        conCuenta(AccountMother.efectivo(), pendiente.accountId());

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "categoryId", ErrorCodes.INVALID_STATE))
                .verify();

        verify(repositorio, never()).confirm(any(), any());
    }

    @Test
    void confirmaElPendienteYLoDevuelveConfirmado() {
        Transaction pendiente = TransactionMother.unGastoPendiente();
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
        conCuenta(AccountMother.efectivo(), pendiente.accountId());
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
        Transaction pendiente = TransactionMother.unGastoPendiente();
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
        conCuenta(AccountMother.efectivo(), pendiente.accountId());
        when(repositorio.confirm(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                .verify();
    }

    @Test
    void unGastoSobreUnaCuentaDesactivadaEsUnConflictoSobreLaCuentaSinConfirmar() {
        Account inactiva = AccountMother.inactiva();
        Transaction pendiente = TransactionMother.conEstado(
                TransactionMother.unGastoGuardadoEn(inactiva.id()), TransactionStatus.PENDING);
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
        conCuenta(inactiva, inactiva.id());

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "accountId", ErrorCodes.INVALID_STATE))
                .verify();

        verify(repositorio, never()).confirm(any(), any());
    }

    @Test
    void unaTransferenciaConElDestinoDesactivadoEsUnConflictoSoloSobreElDestino() {
        Account origen = AccountMother.efectivo();
        Account destino = AccountMother.inactiva();
        conTransferenciaPendiente(origen, destino);

        StepVerifier.create(casoDeUso.approve(USER_ID, TRANSFERENCIA_GUARDADA_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "destinationAccountId",
                        ErrorCodes.INVALID_STATE))
                .verify();

        verify(repositorio, never()).confirm(any(), any());
    }

    @Test
    void unaTransferenciaConLasDosCuentasDesactivadasReportaLasDos() {
        conTransferenciaPendiente(AccountMother.inactiva(), AccountMother.inactivaEnCero());

        StepVerifier.create(casoDeUso.approve(USER_ID, TRANSFERENCIA_GUARDADA_ID))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                            .containsExactlyInAnyOrder(
                                    tuple("accountId", ErrorCodes.INVALID_STATE.getCode()),
                                    tuple("destinationAccountId", ErrorCodes.INVALID_STATE.getCode()));
                })
                .verify();

        verify(repositorio, never()).confirm(any(), any());
    }

    @Test
    void unaTransferenciaConLasDosCuentasActivasSeConfirma() {
        Transaction pendiente = conTransferenciaPendiente(AccountMother.efectivo(), AccountMother.visa());
        when(repositorio.confirm(TRANSFERENCIA_GUARDADA_ID, USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.approve(USER_ID, TRANSFERENCIA_GUARDADA_ID))
                .assertNext(t -> assertThat(t).isEqualTo(
                        TransactionMother.conEstado(pendiente, TransactionStatus.CONFIRMED)))
                .verifyComplete();
    }

    private Transaction conTransferenciaPendiente(Account origen, Account destino) {
        Transaction pendiente = TransactionMother.unaTransferenciaPendienteEntre(origen.id(), destino.id());
        when(repositorio.findByIdAndUser(TRANSFERENCIA_GUARDADA_ID, USER_ID)).thenReturn(Mono.just(pendiente));
        conCuenta(origen, origen.id());
        conCuenta(destino, destino.id());
        return pendiente;
    }

    private void conCuenta(Account cuenta, UUID id) {
        when(cuentas.findActiveByIdAndUser(id, USER_ID)).thenReturn(Mono.just(cuenta));
    }

    static void esError(Throwable error, HttpStatus status, String campo, ErrorCodes codigo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors())
                .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                .containsExactly(tuple(campo, codigo.getCode()));
    }

    @Test
    void aprobarUnGastoSinSaldoEsUnConflictoSobreElMontoYSiguePendiente() {
        Transaction pendiente = TransactionMother.conEstado(
                TransactionMother.en(TransactionMother.unGastoGuardado(), AccountMother.NOMINA_ID, null, "150000"),
                TransactionStatus.PENDING);
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
        conCuenta(AccountMother.nomina("100000"), AccountMother.NOMINA_ID);

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "amount",
                        ErrorCodes.INSUFFICIENT_FUNDS))
                .verify();

        verify(repositorio, never()).confirm(any(), any());
    }

    @Test
    void conLaCuentaDesactivadaSoloSaleElErrorDeEstado() {
        Account inactiva = AccountMother.inactiva();
        Transaction pendiente = TransactionMother.conEstado(
                TransactionMother.en(TransactionMother.unGastoGuardado(), inactiva.id(), null, "500000"),
                TransactionStatus.PENDING);
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
        conCuenta(inactiva, inactiva.id());

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "accountId", ErrorCodes.INVALID_STATE))
                .verify();
    }

    @Test
    void unPendienteProgramadoSeApruebaAunqueSuperaElSaldo() {
        Transaction pendiente = TransactionMother.conFecha(TransactionMother.conEstado(
                TransactionMother.en(TransactionMother.unGastoGuardado(), AccountMother.NOMINA_ID, null, "150000"),
                TransactionStatus.PENDING), RelojFijo.DESPUES);
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
        conCuenta(AccountMother.nomina("100000"), AccountMother.NOMINA_ID);
        when(repositorio.confirm(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID))
                .expectNextCount(1)
                .verifyComplete();
    }
}
