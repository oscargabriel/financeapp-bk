package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.TransactionMother.GASTO_GUARDADO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.TRANSFERENCIA_GUARDADA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.ApprovalChoice;
import com.oscargabriel.financeapp.core.domain.model.ExchangeRate;
import com.oscargabriel.financeapp.core.domain.model.OriginalAmount;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.core.support.AccountMother;
import com.oscargabriel.financeapp.core.support.CurrencyMother;
import com.oscargabriel.financeapp.compartido.support.RelojFijo;
import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Flux;
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

    @Mock
    private AccountQueryPort cuentasDelUsuario;

    @Mock
    private CategoryQueryPort categoriasDelUsuario;

    @Mock
    private CurrencyQueryPort monedas;

    @Mock
    private ResolveExchangeRatePort tasas;

    private ApprovePendingTransactionUseCase casoDeUso;

    /** Mercado viva para todos; cualquier otra categoria, borrada. */
    @BeforeEach
    void reloj() {
        casoDeUso = new ApprovePendingTransactionUseCase(repositorio, cuentas, categorias, cuentasDelUsuario,
                categoriasDelUsuario, monedas, tasas, Clock.fixed(RelojFijo.AHORA, ZoneId.of("America/Bogota")));
        lenient().when(categorias.findActiveByIdAndUser(any(), any())).thenReturn(Mono.empty());
        lenient().when(categorias.findActiveByIdAndUser(TransactionMother.MERCADO_ID, USER_ID))
                .thenReturn(Mono.just(TransactionMother.categoriasDelUsuario().getFirst()));
    }

    @Test
    void unGastoConLaCategoriaBorradaEsUnConflictoSobreLaCategoriaSinConfirmar() {
        Transaction pendiente = TransactionMother.conCategoria(TransactionMother.unGastoPendiente(), UUID.randomUUID());
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
        conCuenta(AccountMother.efectivo(), pendiente.accountId());

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
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

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                .assertNext(t -> assertThat(t).isEqualTo(
                        TransactionMother.conEstado(pendiente, TransactionStatus.CONFIRMED)))
                .verifyComplete();

        verify(repositorio).confirm(GASTO_GUARDADO_ID, USER_ID);
    }

    @Test
    void unMovimientoInexistenteOAjenoEsNoEncontradoSinTocarNada() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                .verify();

        verify(repositorio, never()).confirm(any(), any());
    }

    @Test
    void unMovimientoYaConfirmadoEsUnConflictoSobreElEstado() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(TransactionMother.unGastoGuardado()));

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
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

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
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

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "accountId", ErrorCodes.INVALID_STATE))
                .verify();

        verify(repositorio, never()).confirm(any(), any());
    }

    @Test
    void unaTransferenciaConElDestinoDesactivadoEsUnConflictoSoloSobreElDestino() {
        Account origen = AccountMother.efectivo();
        Account destino = AccountMother.inactiva();
        conTransferenciaPendiente(origen, destino);

        StepVerifier.create(casoDeUso.approve(USER_ID, TRANSFERENCIA_GUARDADA_ID, ApprovalChoice.NONE))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "destinationAccountId",
                        ErrorCodes.INVALID_STATE))
                .verify();

        verify(repositorio, never()).confirm(any(), any());
    }

    @Test
    void unaTransferenciaConLasDosCuentasDesactivadasReportaLasDos() {
        conTransferenciaPendiente(AccountMother.inactiva(), AccountMother.inactivaEnCero());

        StepVerifier.create(casoDeUso.approve(USER_ID, TRANSFERENCIA_GUARDADA_ID, ApprovalChoice.NONE))
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

        StepVerifier.create(casoDeUso.approve(USER_ID, TRANSFERENCIA_GUARDADA_ID, ApprovalChoice.NONE))
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

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
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

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "accountId", ErrorCodes.INVALID_STATE))
                .verify();
    }

    // ---- Pendientes compartidos, sin cuenta ni categoria (FIN-66) ------------------------------------------

    private static final UUID PARTE_PENDIENTE_ID = UUID.fromString("50000000-0000-7000-8000-0000000000aa");
    private static final UUID SHARE_ID = UUID.fromString("72000000-0000-7000-8000-000000000001");
    private static final UUID GRUPO_ID = UUID.fromString("71000000-0000-7000-8000-000000000001");

    /** La parte de USER_ID en un gasto de USUARIO_B, tal como llega: sin cuenta ni categoria. */
    private static Transaction parte(String monto, String moneda) {
        return new Transaction(PARTE_PENDIENTE_ID, USER_ID, TransactionType.EXPENSE, null, null, null,
                new BigDecimal(monto), moneda, "Cena", null, TransactionMother.INSTANTE, TransactionStatus.PENDING,
                TransactionOrigin.WEB, null, null, null, null, GRUPO_ID, TransactionMother.USUARIO_B, SHARE_ID);
    }

    private void conParte(Transaction parte) {
        when(repositorio.findByIdAndUser(PARTE_PENDIENTE_ID, USER_ID)).thenReturn(Mono.just(parte));
        lenient().when(cuentasDelUsuario.findByUser(USER_ID, true))
                .thenReturn(Flux.fromIterable(TransactionMother.cuentasDelUsuario()));
        lenient().when(categoriasDelUsuario.findActiveByUser(eq(USER_ID), any()))
                .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
        lenient().when(monedas.findActive()).thenReturn(Flux.just(CurrencyMother.cop(), CurrencyMother.usd()));
        lenient().when(tasas.refreshToday(any())).thenReturn(Mono.empty());
    }

    private static ApprovalChoice con(UUID cuenta, UUID categoria) {
        return new ApprovalChoice(cuenta == null ? null : cuenta.toString(),
                categoria == null ? null : categoria.toString());
    }

    @Test
    void unPendienteSinCuentaExigeLaCuentaYLaCategoria() {
        conParte(parte("30000", "COP"));

        StepVerifier.create(casoDeUso.approve(USER_ID, PARTE_PENDIENTE_ID, ApprovalChoice.NONE))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                            .containsExactlyInAnyOrder(
                                    tuple("accountId", ErrorCodes.VALIDATION_ERROR.getCode()),
                                    tuple("categoryId", ErrorCodes.VALIDATION_ERROR.getCode()));
                })
                .verify();
        verify(repositorio, never()).payShare(any(), any());
    }

    @Test
    void unPendienteConCuentaNoAceptaCuerpo() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(TransactionMother.unGastoPendiente()));

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID, con(TransactionMother.ORIGEN_ID, null)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, "accountId",
                        ErrorCodes.VALIDATION_ERROR))
                .verify();
        verify(repositorio, never()).confirm(any(), any());
    }

    @Test
    void unaCuentaQueNoEsDelUsuarioEsUnErrorDeLaCuenta() {
        conParte(parte("30000", "COP"));

        StepVerifier.create(casoDeUso.approve(USER_ID, PARTE_PENDIENTE_ID,
                        con(TransactionMother.AJENA_ID, TransactionMother.MERCADO_ID)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, "accountId",
                        ErrorCodes.VALIDATION_ERROR))
                .verify();
        verify(repositorio, never()).payShare(any(), any());
    }

    @Test
    void unaCategoriaDeIngresoParaUnGastoEsUnErrorDeLaCategoria() {
        conParte(parte("30000", "COP"));

        StepVerifier.create(casoDeUso.approve(USER_ID, PARTE_PENDIENTE_ID,
                        con(TransactionMother.ORIGEN_ID, TransactionMother.SALARIO_ID)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, "categoryId",
                        ErrorCodes.VALIDATION_ERROR))
                .verify();
    }

    @Test
    void pagarUnaParteLaConfirmaConLaCuentaElegidaYLeDaUnIngresoPendienteAQuienPago() {
        conParte(parte("30000", "COP"));
        when(repositorio.payShare(any(), any())).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.approve(USER_ID, PARTE_PENDIENTE_ID,
                        con(TransactionMother.ORIGEN_ID, TransactionMother.MERCADO_ID)))
                .assertNext(t -> {
                    assertThat(t.status()).isEqualTo(TransactionStatus.CONFIRMED);
                    assertThat(t.accountId()).isEqualTo(TransactionMother.ORIGEN_ID);
                    assertThat(t.categoryId()).isEqualTo(TransactionMother.MERCADO_ID);
                    assertThat(t.amount()).isEqualByComparingTo("30000");
                    assertThat(t.original()).isNull();
                })
                .verifyComplete();

        ArgumentCaptor<Transaction> confirmado = ArgumentCaptor.forClass(Transaction.class);
        ArgumentCaptor<Transaction> ingreso = ArgumentCaptor.forClass(Transaction.class);
        verify(repositorio).payShare(confirmado.capture(), ingreso.capture());
        assertThat(confirmado.getValue().accountId()).isEqualTo(TransactionMother.ORIGEN_ID);
        Transaction i = ingreso.getValue();
        assertThat(i.userId()).isEqualTo(TransactionMother.USUARIO_B);
        assertThat(i.type()).isEqualTo(TransactionType.INCOME);
        assertThat(i.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(i.amount()).isEqualByComparingTo("30000");
        assertThat(i.currencyCode()).isEqualTo("COP");
        assertThat(i.accountId()).isNull();
        assertThat(i.categoryId()).isNull();
        assertThat(i.description()).isEqualTo("Cena");
        assertThat(i.occurredAt()).isEqualTo(RelojFijo.AHORA);
        assertThat(i.sharedByUserId()).isEqualTo(USER_ID);
        assertThat(i.shareId()).isEqualTo(SHARE_ID);
        assertThat(i.groupId()).isNotNull().isNotEqualTo(GRUPO_ID);
    }

    @Test
    void unaParteEnOtraMonedaSeConvierteALaDeLaCuentaConLaTasaDeSuFecha() {
        conParte(parte("30", "USD"));
        when(tasas.resolve("USD", "COP", LocalDate.of(2026, 9, 20)))
                .thenReturn(Mono.just(new ExchangeRate("USD", "COP", LocalDate.of(2026, 9, 20), new BigDecimal("4100"), LocalDate.of(2026, 9, 20))));
        when(repositorio.payShare(any(), any())).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.approve(USER_ID, PARTE_PENDIENTE_ID,
                        con(TransactionMother.ORIGEN_ID, TransactionMother.MERCADO_ID)))
                .assertNext(t -> {
                    assertThat(t.status()).isEqualTo(TransactionStatus.CONFIRMED);
                    assertThat(t.amount()).isEqualByComparingTo("123000");
                    assertThat(t.currencyCode()).isEqualTo("COP");
                    assertThat(t.original()).isEqualTo(new OriginalAmount(new BigDecimal("30"), "USD"));
                })
                .verifyComplete();

        ArgumentCaptor<Transaction> ingreso = ArgumentCaptor.forClass(Transaction.class);
        verify(repositorio).payShare(any(), ingreso.capture());
        assertThat(ingreso.getValue().amount()).isEqualByComparingTo("30");
        assertThat(ingreso.getValue().currencyCode()).isEqualTo("USD");
    }

    @Test
    void pagarUnaParteSinSaldoEsUnConflictoSobreElMonto() {
        conParte(parte("2000000", "COP"));

        StepVerifier.create(casoDeUso.approve(USER_ID, PARTE_PENDIENTE_ID,
                        con(TransactionMother.ORIGEN_ID, TransactionMother.MERCADO_ID)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "amount",
                        ErrorCodes.INSUFFICIENT_FUNDS))
                .verify();
        verify(repositorio, never()).payShare(any(), any());
    }

    @Test
    void siLaParteYaNoEstaPendienteEsNoEncontrado() {
        conParte(parte("30000", "COP"));
        when(repositorio.payShare(any(), any())).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.approve(USER_ID, PARTE_PENDIENTE_ID,
                        con(TransactionMother.ORIGEN_ID, TransactionMother.MERCADO_ID)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                .verify();
    }

    /** El ingreso de una transferencia compartida no tiene parte: solo se confirma con lo elegido. */
    @Test
    void unIngresoCompartidoSinParteSoloSeConfirma() {
        Transaction ingreso = new Transaction(PARTE_PENDIENTE_ID, USER_ID, TransactionType.INCOME, null, null, null,
                new BigDecimal("60000"), "COP", "Regalo", null, TransactionMother.INSTANTE, TransactionStatus.PENDING,
                TransactionOrigin.WEB, null, null, null, null, GRUPO_ID, TransactionMother.USUARIO_B, null);
        conParte(ingreso);
        when(repositorio.confirmWith(any())).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.approve(USER_ID, PARTE_PENDIENTE_ID,
                        con(TransactionMother.ORIGEN_ID, TransactionMother.SALARIO_ID)))
                .assertNext(t -> {
                    assertThat(t.status()).isEqualTo(TransactionStatus.CONFIRMED);
                    assertThat(t.categoryId()).isEqualTo(TransactionMother.SALARIO_ID);
                })
                .verifyComplete();
        verify(repositorio, never()).payShare(any(), any());
    }

    @Test
    void unPendienteProgramadoSeApruebaAunqueSuperaElSaldo() {
        Transaction pendiente = TransactionMother.conFecha(TransactionMother.conEstado(
                TransactionMother.en(TransactionMother.unGastoGuardado(), AccountMother.NOMINA_ID, null, "150000"),
                TransactionStatus.PENDING), RelojFijo.DESPUES);
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
        conCuenta(AccountMother.nomina("100000"), AccountMother.NOMINA_ID);
        when(repositorio.confirm(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                .expectNextCount(1)
                .verifyComplete();
    }
}
