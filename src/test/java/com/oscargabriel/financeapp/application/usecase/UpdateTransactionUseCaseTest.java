package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.TransactionMother.AJENA_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.DESTINO_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.GASTO_GUARDADO_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.INACTIVA_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.MERCADO_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.ORIGEN_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.SALARIO_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.TRANSFERENCIA_GUARDADA_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.USER_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.unGastoGuardado;
import static com.oscargabriel.financeapp.support.TransactionMother.unGastoGuardadoEn;
import static com.oscargabriel.financeapp.support.TransactionMother.unParche;
import static com.oscargabriel.financeapp.support.TransactionMother.unaTransferenciaGuardada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.InstallmentRef;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UpdateTransactionCommand;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateTransactionUseCaseTest {

    @Mock
    private AccountQueryPort cuentas;

    @Mock
    private CategoryQueryPort categorias;

    @Mock
    private TransactionRepositoryPort repositorio;

    @Captor
    private ArgumentCaptor<Transaction> guardada;

    private UpdateTransactionUseCase casoDeUso;

    @BeforeEach
    void escenario() {
        casoDeUso = new UpdateTransactionUseCase(cuentas, categorias, repositorio);
        when(cuentas.findByUser(USER_ID, true))
                .thenReturn(Flux.fromIterable(TransactionMother.cuentasDelUsuario()));
        when(categorias.findActiveByUser(eq(USER_ID), any()))
                .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(unGastoGuardado()));
        when(repositorio.findByIdAndUser(TRANSFERENCIA_GUARDADA_ID, USER_ID))
                .thenReturn(Mono.just(unaTransferenciaGuardada()));
        when(repositorio.update(any())).thenReturn(Mono.just(true));
    }

    private Mono<Transaction> modificarGasto(UpdateTransactionCommand parche) {
        return casoDeUso.update(USER_ID, GASTO_GUARDADO_ID, parche);
    }

    private Mono<Transaction> modificarTransferencia(UpdateTransactionCommand parche) {
        return casoDeUso.update(USER_ID, TRANSFERENCIA_GUARDADA_ID, parche);
    }

    /** Un 400 con un solo error en el campo dado, y la fila sin tocar. */
    private void rechazaEn(Mono<Transaction> resultado, String campo) {
        StepVerifier.create(resultado)
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                            .containsExactly(tuple(campo,
                                    ErrorCodes.VALIDATION_ERROR.getCode()));
                })
                .verify();
        verify(repositorio, never()).update(any());
    }

    private void respondeNoEncontrado(Mono<Transaction> resultado) {
        StepVerifier.create(resultado)
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                            .containsExactly(tuple("id",
                                    ErrorCodes.NOT_FOUND.getCode()));
                })
                .verify();
    }

    @Test
    void cambiaMontoYDescripcionYConservaElResto() {
        StepVerifier.create(modificarGasto(unParche().amount(new BigDecimal("45000"))
                        .description("  Fruta y verdura  ").build()))
                .assertNext(movimiento -> {
                    assertThat(movimiento.amount()).isEqualByComparingTo("45000");
                    assertThat(movimiento.description()).isEqualTo("Fruta y verdura");
                    assertThat(movimiento.id()).isEqualTo(GASTO_GUARDADO_ID);
                    assertThat(movimiento.userId()).isEqualTo(USER_ID);
                    assertThat(movimiento.type()).isEqualTo(TransactionType.EXPENSE);
                    assertThat(movimiento.accountId()).isEqualTo(ORIGEN_ID);
                    assertThat(movimiento.categoryId()).isEqualTo(MERCADO_ID);
                    assertThat(movimiento.destinationAccountId()).isNull();
                    assertThat(movimiento.notes()).isEqualTo("En la plaza");
                    assertThat(movimiento.currencyCode()).isEqualTo("COP");
                    assertThat(movimiento.occurredAt()).isEqualTo(TransactionMother.INSTANTE);
                })
                .verifyComplete();
    }

    @Test
    void unaCuotaSigueEnSuCompraConSuNumero() {
        UUID compra = UUID.fromString("90000000-0000-7000-8000-000000000001");
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(TransactionMother.cuota(unGastoGuardado(), compra)));

        StepVerifier.create(modificarGasto(unParche().amount(new BigDecimal("400000")).build()))
                .assertNext(movimiento -> assertThat(movimiento.installment())
                        .isEqualTo(new InstallmentRef(compra, 2, 3, new BigDecimal("400000"))))
                .verifyComplete();
    }

    @Test
    void guardaElMovimientoQueDevuelve() {        StepVerifier.create(modificarGasto(unParche().description("Fruta y verdura").build()))
                .expectNextCount(1)
                .verifyComplete();

        verify(repositorio).update(guardada.capture());
        assertThat(guardada.getValue().description()).isEqualTo("Fruta y verdura");
        assertThat(guardada.getValue().amount()).isEqualByComparingTo("30000.5");
    }

    @Test
    void cambiaLaFechaYLaGuardaEnUtc() {
        StepVerifier.create(modificarGasto(unParche().occurredAt("2026-09-21T08:00:00-05:00").build()))
                .assertNext(movimiento -> assertThat(movimiento.occurredAt())
                        .isEqualTo(Instant.parse("2026-09-21T13:00:00Z")))
                .verifyComplete();
    }

    @Test
    void cambiaLaCuentaPorOtraPropia() {
        StepVerifier.create(modificarGasto(unParche().accountId(DESTINO_ID.toString()).build()))
                .assertNext(movimiento -> assertThat(movimiento.accountId()).isEqualTo(DESTINO_ID))
                .verifyComplete();
    }

    @Test
    void gastoATransferenciaVaciaLaCategoria() {
        StepVerifier.create(modificarGasto(unParche().type("transfer")
                        .destinationAccountId(DESTINO_ID.toString()).build()))
                .assertNext(movimiento -> {
                    assertThat(movimiento.type()).isEqualTo(TransactionType.TRANSFER);
                    assertThat(movimiento.destinationAccountId()).isEqualTo(DESTINO_ID);
                    assertThat(movimiento.categoryId()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void gastoATransferenciaSinDestinoSeRechaza() {
        rechazaEn(modificarGasto(unParche().type("TRANSFER").build()), "destinationAccountId");
    }

    @Test
    void transferenciaAGastoVaciaElDestino() {
        StepVerifier.create(modificarTransferencia(unParche().type("EXPENSE")
                        .categoryId(MERCADO_ID.toString()).build()))
                .assertNext(movimiento -> {
                    assertThat(movimiento.type()).isEqualTo(TransactionType.EXPENSE);
                    assertThat(movimiento.categoryId()).isEqualTo(MERCADO_ID);
                    assertThat(movimiento.destinationAccountId()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void transferenciaAGastoSinCategoriaSeRechaza() {
        rechazaEn(modificarTransferencia(unParche().type("EXPENSE").build()), "categoryId");
    }

    @Test
    void gastoAIngresoConservaLaCategoriaSiEsCompatible() {
        StepVerifier.create(modificarGasto(unParche().type("INCOME").categoryId(SALARIO_ID.toString()).build()))
                .assertNext(movimiento -> {
                    assertThat(movimiento.type()).isEqualTo(TransactionType.INCOME);
                    assertThat(movimiento.categoryId()).isEqualTo(SALARIO_ID);
                })
                .verifyComplete();
    }

    @Test
    void gastoAIngresoConLaCategoriaDeGastoGuardadaSeRechaza() {
        rechazaEn(modificarGasto(unParche().type("INCOME").build()), "categoryId");
    }

    @Test
    void unaCategoriaEnUnaTransferenciaSeRechaza() {
        rechazaEn(modificarTransferencia(unParche().categoryId(MERCADO_ID.toString()).build()), "categoryId");
    }

    @Test
    void unaCuentaDestinoEnUnGastoSeRechaza() {
        rechazaEn(modificarGasto(unParche().destinationAccountId(DESTINO_ID.toString()).build()),
                "destinationAccountId");
    }

    @Test
    void elOrigenNoPuedeSerElDestinoDeLaTransferencia() {
        rechazaEn(modificarTransferencia(unParche().accountId(DESTINO_ID.toString()).build()),
                "destinationAccountId");
    }

    @Test
    void unaCuentaAjenaSeRechaza() {
        rechazaEn(modificarGasto(unParche().accountId(AJENA_ID.toString()).build()), "accountId");
    }

    @Test
    void unaCuentaMalFormadaSeRechazaComoInexistente() {
        rechazaEn(modificarGasto(unParche().accountId("abc").build()), "accountId");
    }

    @Test
    void unaCategoriaAjenaSeRechaza() {
        rechazaEn(modificarGasto(unParche().categoryId(UUID.randomUUID().toString()).build()), "categoryId");
    }

    @Test
    void reuneTodosLosErroresDelParche() {
        StepVerifier.create(modificarGasto(unParche().accountId(AJENA_ID.toString())
                        .destinationAccountId(DESTINO_ID.toString()).build()))
                .expectErrorSatisfies(error -> assertThat(((BadRequestException) error).getErrorResponse()
                        .getErrors())
                        .extracting(ErrorDetail::getField)
                        .containsExactlyInAnyOrder("accountId", "destinationAccountId"))
                .verify();
    }

    @Test
    void laCuentaGuardadaDesactivadaNoImpideCorregirLaDescripcion() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(unGastoGuardadoEn(INACTIVA_ID)));

        StepVerifier.create(modificarGasto(unParche().description("Fruta").build()))
                .assertNext(movimiento -> assertThat(movimiento.accountId()).isEqualTo(INACTIVA_ID))
                .verifyComplete();
    }

    @Test
    void unMovimientoInexistenteOAjenoEsNoEncontrado() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.empty());

        respondeNoEncontrado(modificarGasto(unParche().description("Fruta").build()));
        verify(repositorio, never()).update(any());
    }

    @Test
    void siElMovimientoDesapareceAntesDelUpdateEsNoEncontrado() {
        when(repositorio.update(any())).thenReturn(Mono.just(false));

        respondeNoEncontrado(modificarGasto(unParche().description("Fruta").build()));
    }
}
