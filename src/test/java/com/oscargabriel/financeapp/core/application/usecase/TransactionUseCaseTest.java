package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.AccountMother.BOLSILLO_ID;
import static com.oscargabriel.financeapp.core.support.AccountMother.NOMINA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.AJENA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.DESTINO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.GASTO_GUARDADO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.INACTIVA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.INGRESO_GUARDADO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.MERCADO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.ORIGEN_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.SALARIO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.TRANSFERENCIA_GUARDADA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.USD_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.USER_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.conEstado;
import static com.oscargabriel.financeapp.core.support.TransactionMother.conFecha;
import static com.oscargabriel.financeapp.core.support.TransactionMother.en;
import static com.oscargabriel.financeapp.core.support.TransactionMother.unGastoGuardado;
import static com.oscargabriel.financeapp.core.support.TransactionMother.unGastoGuardadoEn;
import static com.oscargabriel.financeapp.core.support.TransactionMother.unIngresoGuardado;
import static com.oscargabriel.financeapp.core.support.TransactionMother.unParche;
import static com.oscargabriel.financeapp.core.support.TransactionMother.unaTransferenciaGuardada;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.compartido.support.RelojFijo;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.ApprovalChoice;
import com.oscargabriel.financeapp.core.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.core.domain.model.ExchangeRate;
import com.oscargabriel.financeapp.core.domain.model.InstallmentRef;
import com.oscargabriel.financeapp.core.domain.model.OriginalAmount;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.model.UpdateTransactionCommand;
import com.oscargabriel.financeapp.core.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.core.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.core.support.AccountMother;
import com.oscargabriel.financeapp.core.support.CurrencyMother;
import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TransactionUseCaseTest {

    private static final Clock RELOJ = Clock.fixed(RelojFijo.AHORA, ZoneId.of("America/Bogota"));

    private static final UUID GRUPO_ID = UUID.fromString("71000000-0000-7000-8000-000000000001");

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

    @Mock
    private CreateTransactionsPort alta;

    @Captor
    private ArgumentCaptor<Transaction> guardada;

    @Nested
    class Crear {

        @Test
        void delegaElLoteEnElAltaYDevuelveLoQueEsta() {
            List<CreateTransactionCommand> lote = List.of(TransactionMother.unGasto().build());
            Transaction creado = unGastoGuardado();
            when(alta.create(USER_ID, TransactionOrigin.WEB, TransactionStatus.CONFIRMED, lote))
                    .thenReturn(Flux.just(creado));

            StepVerifier.create(useCase().create(USER_ID, TransactionOrigin.WEB, TransactionStatus.CONFIRMED, lote))
                    .expectNext(creado)
                    .verifyComplete();
        }
    }

    @Nested
    class Actualizar {

        @BeforeEach
        void escenario() {
            lenient().when(cuentasDelUsuario.findByUser(USER_ID, true))
                    .thenReturn(Flux.fromIterable(TransactionMother.cuentasDelUsuario()));
            lenient().when(categoriasDelUsuario.findActiveByUser(eq(USER_ID), any()))
                    .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
            lenient().when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(unGastoGuardado()));
            lenient().when(repositorio.findByIdAndUser(TRANSFERENCIA_GUARDADA_ID, USER_ID))
                    .thenReturn(Mono.just(unaTransferenciaGuardada()));
            lenient().when(repositorio.update(any())).thenReturn(Mono.just(true));
            lenient().when(repositorio.hasPendingShares(any())).thenReturn(Mono.just(false));
        }

        private Mono<Transaction> modificarGasto(UpdateTransactionCommand parche) {
            return useCase().update(USER_ID, GASTO_GUARDADO_ID, parche);
        }

        private Mono<Transaction> modificarTransferencia(UpdateTransactionCommand parche) {
            return useCase().update(USER_ID, TRANSFERENCIA_GUARDADA_ID, parche);
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
        void guardaElMovimientoQueDevuelve() {
            StepVerifier.create(modificarGasto(unParche().description("Fruta y verdura").build()))
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

        /** FA-51: currencyCode no cambia, asi que la cuenta nueva tiene que estar en la moneda del movimiento. */
        @Test
        void unaCuentaEnOtraMonedaSeRechaza() {
            rechazaEn(modificarGasto(unParche().accountId(USD_ID.toString()).build()), "accountId");
        }

        @Test
        void unMontoDeDestinoEnUnGastoSeRechaza() {
            rechazaEn(modificarGasto(unParche().destinationAmount(BigDecimal.TEN).build()), "destinationAmount");
        }

        @Test
        void unMontoDeDestinoEntreCuentasDeLaMismaMonedaSeRechaza() {
            rechazaEn(modificarTransferencia(unParche().destinationAmount(BigDecimal.TEN).build()),
                    "destinationAmount");
        }

        @Test
        void gastoATransferenciaEntreMonedasSinMontoDeDestinoSeRechaza() {
            rechazaEn(modificarGasto(unParche().type("TRANSFER").destinationAccountId(USD_ID.toString()).build()),
                    "destinationAmount");
        }

        @Test
        void gastoATransferenciaEntreMonedasConMontoDeDestino() {
            StepVerifier.create(modificarGasto(unParche().type("TRANSFER").destinationAccountId(USD_ID.toString())
                            .destinationAmount(new BigDecimal("7.5")).build()))
                    .assertNext(movimiento -> {
                        assertThat(movimiento.destinationAccountId()).isEqualTo(USD_ID);
                        assertThat(movimiento.destinationAmount()).isEqualByComparingTo("7.5");
                    })
                    .verifyComplete();
        }

        @Test
        void ajustaElMontoDeDestinoDeUnaTransferenciaEntreMonedas() {
            transferenciaEntreMonedasGuardada();

            StepVerifier.create(modificarTransferencia(unParche().destinationAmount(new BigDecimal("90")).build()))
                    .assertNext(movimiento -> assertThat(movimiento.destinationAmount()).isEqualByComparingTo("90"))
                    .verifyComplete();
        }

        @Test
        void conservaElMontoDeDestinoMientrasElDestinoSigaEnSuMoneda() {
            transferenciaEntreMonedasGuardada();

            StepVerifier.create(modificarTransferencia(unParche().amount(new BigDecimal("400000")).build()))
                    .assertNext(movimiento -> assertThat(movimiento.destinationAmount()).isEqualByComparingTo("95"))
                    .verifyComplete();
        }

        @Test
        void unaTransferenciaEntreMonedasQuePasaAGastoPierdeElMontoDeDestino() {
            transferenciaEntreMonedasGuardada();

            StepVerifier.create(modificarTransferencia(unParche().type("EXPENSE").categoryId(MERCADO_ID.toString())
                            .build()))
                    .assertNext(movimiento -> {
                        assertThat(movimiento.destinationAccountId()).isNull();
                        assertThat(movimiento.destinationAmount()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        void unDestinoEnLaMonedaDelMovimientoPierdeElMontoDeDestino() {
            transferenciaEntreMonedasGuardada();

            StepVerifier.create(modificarTransferencia(unParche().destinationAccountId(DESTINO_ID.toString()).build()))
                    .assertNext(movimiento -> assertThat(movimiento.destinationAmount()).isNull())
                    .verifyComplete();
        }

        @Test
        void elMontoOriginalDeUnConvertidoNoCambiaAlAjustarElMonto() {
            OriginalAmount original = new OriginalAmount(new BigDecimal("8"), "USD");
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(TransactionMother.convertidoDesde(unGastoGuardado(), original)));

            StepVerifier.create(modificarGasto(unParche().amount(new BigDecimal("32000")).build()))
                    .assertNext(movimiento -> {
                        assertThat(movimiento.amount()).isEqualByComparingTo("32000");
                        assertThat(movimiento.original()).isEqualTo(original);
                    })
                    .verifyComplete();
        }

        /** De Efectivo (COP) a la cuenta en USD, con 95 dolares de destino. */
        private void transferenciaEntreMonedasGuardada() {
            when(repositorio.findByIdAndUser(TRANSFERENCIA_GUARDADA_ID, USER_ID)).thenReturn(Mono.just(
                    TransactionMother.haciaOtraMoneda(unaTransferenciaGuardada(), USD_ID, new BigDecimal("95"))));
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

        /** Las cuentas del usuario mas Nomina y Bolsillo con los saldos dados. */
        private void conSaldos(Account... extra) {
            when(cuentasDelUsuario.findByUser(USER_ID, true)).thenReturn(Flux.fromStream(Stream.concat(
                    TransactionMother.cuentasDelUsuario().stream(), Stream.of(extra))));
        }

        private void rechazaPorSaldo(Mono<Transaction> resultado, String descripcion) {
            StepVerifier.create(resultado)
                    .expectErrorSatisfies(error -> {
                        BadRequestException bre = (BadRequestException) error;
                        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(bre.getErrorResponse().getErrors())
                                .extracting(ErrorDetail::getCode, ErrorDetail::getField, ErrorDetail::getDescription)
                                .containsExactly(tuple(ErrorCodes.INSUFFICIENT_FUNDS.getCode(), "amount", descripcion));
                    })
                    .verify();
            verify(repositorio, never()).update(any());
        }

        @Test
        void subirUnGastoPorEncimaDelSaldoSeRechaza() {
            rechazaPorSaldo(modificarGasto(unParche().amount(new BigDecimal("1100000")).build()),
                    "Saldo insuficiente en Efectivo: quedaría en -69999.5 COP");
        }

        @Test
        void cambiarElGastoAUnaCuentaSinSaldoSeRechaza() {
            conSaldos(AccountMother.bolsillo("10000"));

            rechazaPorSaldo(modificarGasto(unParche().accountId(BOLSILLO_ID.toString()).build()),
                    "Saldo insuficiente en Bolsillo: quedaría en -20000.5 COP");
        }

        @Test
        void reducirUnIngresoYaGastadoSeRechaza() {
            conSaldos(AccountMother.nomina("10000"));
            when(repositorio.findByIdAndUser(INGRESO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(en(unIngresoGuardado(), NOMINA_ID, null, "100000")));

            rechazaPorSaldo(useCase().update(USER_ID, INGRESO_GUARDADO_ID,
                    unParche().amount(new BigDecimal("50000")).build()),
                    "Saldo insuficiente en Nómina: quedaría en -40000 COP");
        }

        @Test
        void pasarUnIngresoYaGastadoAFechaFuturaSeRechaza() {
            conSaldos(AccountMother.nomina("10000"));
            when(repositorio.findByIdAndUser(INGRESO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(en(unIngresoGuardado(), NOMINA_ID, null, "100000")));

            rechazaPorSaldo(useCase().update(USER_ID, INGRESO_GUARDADO_ID,
                    unParche().occurredAt("2026-12-01T10:00:00-05:00").build()),
                    "Saldo insuficiente en Nómina: quedaría en -90000 COP");
        }

        @Test
        void bajarUnGastoDeUnaCuentaEnNegativoSePermite() {
            conSaldos(AccountMother.bolsillo("-5000"));
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(en(unGastoGuardado(), BOLSILLO_ID, null, "30000")));

            StepVerifier.create(modificarGasto(unParche().amount(new BigDecimal("20000")).build()))
                    .assertNext(t -> assertThat(t.amount()).isEqualByComparingTo("20000"))
                    .verifyComplete();
        }

        @Test
        void unPendienteNoSeValidaContraElSaldo() {
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(conEstado(unGastoGuardado(), TransactionStatus.PENDING)));

            StepVerifier.create(modificarGasto(unParche().amount(new BigDecimal("5000000")).build()))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        // ---- Compartidos (FIN-66) ------------------------------------------------------------------------

        /** Un 409 INVALID_STATE con un solo error en el campo dado, y la fila sin tocar. */
        private void enConflictoEn(Mono<Transaction> resultado, String campo) {
            StepVerifier.create(resultado)
                    .expectErrorSatisfies(error -> {
                        BadRequestException bre = (BadRequestException) error;
                        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(bre.getErrorResponse().getErrors())
                                .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                                .containsExactly(tuple(campo, ErrorCodes.INVALID_STATE.getCode()));
                    })
                    .verify();
            verify(repositorio, never()).update(any());
        }

        @Test
        void unPendienteQueCompartioOtraPersonaNoSeModifica() {
            Transaction g = unGastoGuardado();
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(new Transaction(g.id(),
                    g.userId(), g.type(), null, null, null, g.amount(), g.currencyCode(), g.description(), null,
                    g.occurredAt(), TransactionStatus.PENDING, g.origin(), null, null, null, null, UUID.randomUUID(),
                    TransactionMother.USUARIO_B, UUID.randomUUID())));

            enConflictoEn(modificarGasto(unParche().description("Otra").build()), "status");
        }

        @Test
        void elMontoDeUnGastoConPartesPendientesNoCambia() {
            when(repositorio.hasPendingShares(GASTO_GUARDADO_ID)).thenReturn(Mono.just(true));

            enConflictoEn(modificarGasto(unParche().amount(new BigDecimal("1000")).build()), "amount");
        }

        @Test
        void elTipoDeUnGastoConPartesPendientesNoCambia() {
            when(repositorio.hasPendingShares(GASTO_GUARDADO_ID)).thenReturn(Mono.just(true));

            enConflictoEn(modificarGasto(unParche().type("INCOME").categoryId(SALARIO_ID.toString())
                    .build()), "type");
        }

        /** El parche no cambia el monto, asi que no se consulta: el stub es lenient para dejarlo dicho. */
        @Test
        void laDescripcionDeUnGastoConPartesPendientesSiCambia() {
            lenient().when(repositorio.hasPendingShares(GASTO_GUARDADO_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(modificarGasto(unParche().description("Cena de cumpleanos").amount(
                            unGastoGuardado().amount()).build()))
                    .assertNext(t -> assertThat(t.description()).isEqualTo("Cena de cumpleanos"))
                    .verifyComplete();
        }
    }

    @Nested
    class Borrar {

        @BeforeEach
        void escenario() {
            lenient().when(cuentasDelUsuario.findByUser(USER_ID, true))
                    .thenReturn(Flux.just(AccountMother.nomina("10000"), AccountMother.bolsillo("5000")));
            lenient().when(repositorio.deleteByIdAndUser(any(), any())).thenReturn(Mono.just(true));
        }

        private void rechazaPorSaldo(UUID id, String descripcion) {
            StepVerifier.create(useCase().delete(USER_ID, id))
                    .expectErrorSatisfies(error -> {
                        BadRequestException bre = (BadRequestException) error;
                        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(bre.getErrorResponse().getErrors())
                                .extracting(ErrorDetail::getCode, ErrorDetail::getField, ErrorDetail::getDescription)
                                .containsExactly(tuple(ErrorCodes.INSUFFICIENT_FUNDS.getCode(), "id", descripcion));
                    })
                    .verify();
            verify(repositorio, never()).deleteByIdAndUser(any(), any());
        }

        @Test
        void borraElMovimientoDelUsuarioYCompletaVacio() {
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(en(unGastoGuardado(), NOMINA_ID, null, "30000")));

            StepVerifier.create(useCase().delete(USER_ID, GASTO_GUARDADO_ID))
                    .verifyComplete();

            verify(repositorio).deleteByIdAndUser(GASTO_GUARDADO_ID, USER_ID);
        }

        /** Un pendiente compartido solo se aprueba o se rechaza: borrarlo no puede saltarse el rechazo (FIN-66). */
        @Test
        void unPendienteQueCompartioOtraPersonaNoSeBorra() {
            Transaction g = unGastoGuardado();
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(new Transaction(g.id(),
                    g.userId(), g.type(), null, null, null, g.amount(), g.currencyCode(), g.description(), null,
                    g.occurredAt(), TransactionStatus.PENDING, g.origin(), null, null, null, null, UUID.randomUUID(),
                    TransactionMother.USUARIO_B, UUID.randomUUID())));

            StepVerifier.create(useCase().delete(USER_ID, GASTO_GUARDADO_ID))
                    .expectErrorSatisfies(error -> {
                        BadRequestException bre = (BadRequestException) error;
                        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(bre.getErrorResponse().getErrors())
                                .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                                .containsExactly(tuple("status", ErrorCodes.INVALID_STATE.getCode()));
                    })
                    .verify();
            verify(repositorio, never()).deleteByIdAndUser(any(), any());
        }

        @Test
        void unMovimientoInexistenteOAjenoEsNoEncontrado() {
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().delete(USER_ID, GASTO_GUARDADO_ID))
                    .expectErrorSatisfies(error -> {
                        BadRequestException bre = (BadRequestException) error;
                        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(bre.getErrorResponse().getErrors())
                                .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                                .containsExactly(tuple("id", ErrorCodes.NOT_FOUND.getCode()));
                    })
                    .verify();
            verify(repositorio, never()).deleteByIdAndUser(any(), any());
        }

        @Test
        void siDesaparecioAntesDeBorrarloEsNoEncontrado() {
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(en(unGastoGuardado(), NOMINA_ID, null, "30000")));
            when(repositorio.deleteByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().delete(USER_ID, GASTO_GUARDADO_ID))
                    .expectErrorSatisfies(error -> assertThat(((BadRequestException) error).getHttpStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND))
                    .verify();
        }

        @Test
        void borrarUnIngresoYaGastadoSeRechaza() {
            when(repositorio.findByIdAndUser(INGRESO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(en(unIngresoGuardado(), NOMINA_ID, null, "100000")));

            rechazaPorSaldo(INGRESO_GUARDADO_ID, "Saldo insuficiente en Nómina: quedaría en -90000 COP");
        }

        @Test
        void borrarUnaTransferenciaQueElDestinoYaGastoSeRechaza() {
            when(repositorio.findByIdAndUser(TRANSFERENCIA_GUARDADA_ID, USER_ID))
                    .thenReturn(Mono.just(en(unaTransferenciaGuardada(), NOMINA_ID, BOLSILLO_ID, "50000")));

            rechazaPorSaldo(TRANSFERENCIA_GUARDADA_ID, "Saldo insuficiente en Bolsillo: quedaría en -45000 COP");
        }

        @Test
        void borrarUnIngresoProgramadoSePermite() {
            when(repositorio.findByIdAndUser(INGRESO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(conFecha(en(unIngresoGuardado(), NOMINA_ID, null, "100000"),
                            RelojFijo.DESPUES)));

            StepVerifier.create(useCase().delete(USER_ID, INGRESO_GUARDADO_ID))
                    .verifyComplete();
        }
    }

    @Nested
    class Pendientes {

        @Test
        void devuelveLosPendientesDelUsuarioEnElOrdenDelRepositorio() {
            Transaction primero = TransactionMother.unGastoPendiente();
            Transaction segundo = conEstado(unaTransferenciaGuardada(), primero.status());
            when(repositorio.findPendingByUser(USER_ID)).thenReturn(Flux.just(primero, segundo));

            StepVerifier.create(useCase().pending(USER_ID, Optional.empty()))
                    .expectNext(primero, segundo)
                    .verifyComplete();

            verify(repositorio, never()).findPendingSharedBy(any(), any());
        }

        @Test
        void conFiltroPideSoloLosDeEsaPersonaConElCorreoNormalizado() {
            Transaction compartido = TransactionMother.unGastoPendiente();
            when(repositorio.findPendingSharedBy(USER_ID, "laura@correo.com")).thenReturn(Flux.just(compartido));

            StepVerifier.create(useCase().pending(USER_ID, Optional.of("  Laura@Correo.COM ")))
                    .expectNext(compartido)
                    .verifyComplete();

            verify(repositorio, never()).findPendingByUser(any());
        }
    }

    @Nested
    class Aprobar {

        private static final UUID PARTE_PENDIENTE_ID = UUID.fromString("50000000-0000-7000-8000-0000000000aa");
        private static final UUID SHARE_ID = UUID.fromString("72000000-0000-7000-8000-000000000001");

        /** Mercado viva para todos; cualquier otra categoria, borrada. */
        @BeforeEach
        void mercadoViva() {
            lenient().when(categorias.findActiveByIdAndUser(any(), any())).thenReturn(Mono.empty());
            lenient().when(categorias.findActiveByIdAndUser(MERCADO_ID, USER_ID))
                    .thenReturn(Mono.just(TransactionMother.categoriasDelUsuario().getFirst()));
        }

        @Test
        void unGastoConLaCategoriaBorradaEsUnConflictoSobreLaCategoriaSinConfirmar() {
            Transaction pendiente = TransactionMother.conCategoria(TransactionMother.unGastoPendiente(),
                    UUID.randomUUID());
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
            conCuenta(AccountMother.efectivo(), pendiente.accountId());

            StepVerifier.create(useCase().approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "categoryId",
                            ErrorCodes.INVALID_STATE))
                    .verify();

            verify(repositorio, never()).confirm(any(), any());
        }

        @Test
        void confirmaElPendienteYLoDevuelveConfirmado() {
            Transaction pendiente = TransactionMother.unGastoPendiente();
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
            conCuenta(AccountMother.efectivo(), pendiente.accountId());
            when(repositorio.confirm(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                    .assertNext(t -> assertThat(t).isEqualTo(conEstado(pendiente, TransactionStatus.CONFIRMED)))
                    .verifyComplete();

            verify(repositorio).confirm(GASTO_GUARDADO_ID, USER_ID);
        }

        /** FIN-101 */
        @Test
        void aprobarUnPendienteQueLlegoPorCorreoConservaSuOrigenYSuGrupo() {
            Transaction pendiente = TransactionMother.enGrupo(
                    TransactionMother.conOrigen(TransactionMother.unGastoPendiente(), TransactionOrigin.EMAIL),
                    GRUPO_ID);
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
            conCuenta(AccountMother.efectivo(), pendiente.accountId());
            when(repositorio.confirm(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                    .assertNext(t -> assertThat(t)
                            .extracting(Transaction::status, Transaction::origin, Transaction::groupId)
                            .containsExactly(TransactionStatus.CONFIRMED, TransactionOrigin.EMAIL, GRUPO_ID))
                    .verifyComplete();
        }

        @Test
        void unMovimientoInexistenteOAjenoEsNoEncontradoSinTocarNada() {
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                    .verify();

            verify(repositorio, never()).confirm(any(), any());
        }

        @Test
        void unMovimientoYaConfirmadoEsUnConflictoSobreElEstado() {
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(unGastoGuardado()));

            StepVerifier.create(useCase().approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "status",
                            ErrorCodes.INVALID_STATE))
                    .verify();

            verify(repositorio, never()).confirm(any(), any());
        }

        @Test
        void siOtroRequestLoConfirmoOBorroEnMedioEsNoEncontrado() {
            Transaction pendiente = TransactionMother.unGastoPendiente();
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
            conCuenta(AccountMother.efectivo(), pendiente.accountId());
            when(repositorio.confirm(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                    .verify();
        }

        @Test
        void unGastoSobreUnaCuentaDesactivadaEsUnConflictoSobreLaCuentaSinConfirmar() {
            Account inactiva = AccountMother.inactiva();
            Transaction pendiente = conEstado(unGastoGuardadoEn(inactiva.id()), TransactionStatus.PENDING);
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
            conCuenta(inactiva, inactiva.id());

            StepVerifier.create(useCase().approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "accountId",
                            ErrorCodes.INVALID_STATE))
                    .verify();

            verify(repositorio, never()).confirm(any(), any());
        }

        @Test
        void unaTransferenciaConElDestinoDesactivadoEsUnConflictoSoloSobreElDestino() {
            conTransferenciaPendiente(AccountMother.efectivo(), AccountMother.inactiva());

            StepVerifier.create(useCase().approve(USER_ID, TRANSFERENCIA_GUARDADA_ID, ApprovalChoice.NONE))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "destinationAccountId",
                            ErrorCodes.INVALID_STATE))
                    .verify();

            verify(repositorio, never()).confirm(any(), any());
        }

        @Test
        void unaTransferenciaConLasDosCuentasDesactivadasReportaLasDos() {
            conTransferenciaPendiente(AccountMother.inactiva(), AccountMother.inactivaEnCero());

            StepVerifier.create(useCase().approve(USER_ID, TRANSFERENCIA_GUARDADA_ID, ApprovalChoice.NONE))
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

            StepVerifier.create(useCase().approve(USER_ID, TRANSFERENCIA_GUARDADA_ID, ApprovalChoice.NONE))
                    .assertNext(t -> assertThat(t).isEqualTo(conEstado(pendiente, TransactionStatus.CONFIRMED)))
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

        @Test
        void aprobarUnGastoSinSaldoEsUnConflictoSobreElMontoYSiguePendiente() {
            Transaction pendiente = conEstado(en(unGastoGuardado(), NOMINA_ID, null, "150000"),
                    TransactionStatus.PENDING);
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
            conCuenta(AccountMother.nomina("100000"), NOMINA_ID);

            StepVerifier.create(useCase().approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "amount",
                            ErrorCodes.INSUFFICIENT_FUNDS))
                    .verify();

            verify(repositorio, never()).confirm(any(), any());
        }

        @Test
        void conLaCuentaDesactivadaSoloSaleElErrorDeEstado() {
            Account inactiva = AccountMother.inactiva();
            Transaction pendiente = conEstado(en(unGastoGuardado(), inactiva.id(), null, "500000"),
                    TransactionStatus.PENDING);
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
            conCuenta(inactiva, inactiva.id());

            StepVerifier.create(useCase().approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "accountId",
                            ErrorCodes.INVALID_STATE))
                    .verify();
        }

        // ---- Pendientes compartidos, sin cuenta ni categoria (FIN-66) --------------------------------------

        /** La parte de USER_ID en un gasto de USUARIO_B, tal como llega: sin cuenta ni categoria. */
        private static Transaction parte(String monto, String moneda) {
            return new Transaction(PARTE_PENDIENTE_ID, USER_ID, TransactionType.EXPENSE, null, null, null,
                    new BigDecimal(monto), moneda, "Cena", null, TransactionMother.INSTANTE,
                    TransactionStatus.PENDING, TransactionOrigin.WEB, null, null, null, null, GRUPO_ID,
                    TransactionMother.USUARIO_B, SHARE_ID);
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

            StepVerifier.create(useCase().approve(USER_ID, PARTE_PENDIENTE_ID, ApprovalChoice.NONE))
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

            StepVerifier.create(useCase().approve(USER_ID, GASTO_GUARDADO_ID, con(ORIGEN_ID, null)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, "accountId",
                            ErrorCodes.VALIDATION_ERROR))
                    .verify();
            verify(repositorio, never()).confirm(any(), any());
        }

        @Test
        void unaCuentaQueNoEsDelUsuarioEsUnErrorDeLaCuenta() {
            conParte(parte("30000", "COP"));

            StepVerifier.create(useCase().approve(USER_ID, PARTE_PENDIENTE_ID, con(AJENA_ID, MERCADO_ID)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, "accountId",
                            ErrorCodes.VALIDATION_ERROR))
                    .verify();
            verify(repositorio, never()).payShare(any(), any());
        }

        @Test
        void unaCategoriaDeIngresoParaUnGastoEsUnErrorDeLaCategoria() {
            conParte(parte("30000", "COP"));

            StepVerifier.create(useCase().approve(USER_ID, PARTE_PENDIENTE_ID, con(ORIGEN_ID, SALARIO_ID)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, "categoryId",
                            ErrorCodes.VALIDATION_ERROR))
                    .verify();
        }

        @Test
        void pagarUnaParteLaConfirmaConLaCuentaElegidaYLeDaUnIngresoPendienteAQuienPago() {
            conParte(parte("30000", "COP"));
            when(repositorio.payShare(any(), any())).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().approve(USER_ID, PARTE_PENDIENTE_ID, con(ORIGEN_ID, MERCADO_ID)))
                    .assertNext(t -> {
                        assertThat(t.status()).isEqualTo(TransactionStatus.CONFIRMED);
                        assertThat(t.accountId()).isEqualTo(ORIGEN_ID);
                        assertThat(t.categoryId()).isEqualTo(MERCADO_ID);
                        assertThat(t.amount()).isEqualByComparingTo("30000");
                        assertThat(t.original()).isNull();
                    })
                    .verifyComplete();

            ArgumentCaptor<Transaction> confirmado = ArgumentCaptor.forClass(Transaction.class);
            ArgumentCaptor<Transaction> ingreso = ArgumentCaptor.forClass(Transaction.class);
            verify(repositorio).payShare(confirmado.capture(), ingreso.capture());
            assertThat(confirmado.getValue().accountId()).isEqualTo(ORIGEN_ID);
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
                    .thenReturn(Mono.just(new ExchangeRate("USD", "COP", LocalDate.of(2026, 9, 20),
                            new BigDecimal("4100"), LocalDate.of(2026, 9, 20))));
            when(repositorio.payShare(any(), any())).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().approve(USER_ID, PARTE_PENDIENTE_ID, con(ORIGEN_ID, MERCADO_ID)))
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

            StepVerifier.create(useCase().approve(USER_ID, PARTE_PENDIENTE_ID, con(ORIGEN_ID, MERCADO_ID)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "amount",
                            ErrorCodes.INSUFFICIENT_FUNDS))
                    .verify();
            verify(repositorio, never()).payShare(any(), any());
        }

        @Test
        void siLaParteYaNoEstaPendienteEsNoEncontrado() {
            conParte(parte("30000", "COP"));
            when(repositorio.payShare(any(), any())).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().approve(USER_ID, PARTE_PENDIENTE_ID, con(ORIGEN_ID, MERCADO_ID)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                    .verify();
        }

        /** El ingreso de una transferencia compartida no tiene parte: solo se confirma con lo elegido. */
        @Test
        void unIngresoCompartidoSinParteSoloSeConfirma() {
            Transaction ingreso = new Transaction(PARTE_PENDIENTE_ID, USER_ID, TransactionType.INCOME, null, null,
                    null, new BigDecimal("60000"), "COP", "Regalo", null, TransactionMother.INSTANTE,
                    TransactionStatus.PENDING, TransactionOrigin.WEB, null, null, null, null, GRUPO_ID,
                    TransactionMother.USUARIO_B, null);
            conParte(ingreso);
            when(repositorio.confirmWith(any())).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().approve(USER_ID, PARTE_PENDIENTE_ID, con(ORIGEN_ID, SALARIO_ID)))
                    .assertNext(t -> {
                        assertThat(t.status()).isEqualTo(TransactionStatus.CONFIRMED);
                        assertThat(t.categoryId()).isEqualTo(SALARIO_ID);
                    })
                    .verifyComplete();
            verify(repositorio, never()).payShare(any(), any());
        }

        @Test
        void unPendienteProgramadoSeApruebaAunqueSuperaElSaldo() {
            Transaction pendiente = conFecha(conEstado(en(unGastoGuardado(), NOMINA_ID, null, "150000"),
                    TransactionStatus.PENDING), RelojFijo.DESPUES);
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(pendiente));
            conCuenta(AccountMother.nomina("100000"), NOMINA_ID);
            when(repositorio.confirm(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().approve(USER_ID, GASTO_GUARDADO_ID, ApprovalChoice.NONE))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    @Nested
    class Rechazar {

        @Test
        void borraElPendienteYCompletaVacio() {
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(TransactionMother.unGastoPendiente()));
            when(repositorio.deletePending(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().reject(USER_ID, GASTO_GUARDADO_ID))
                    .verifyComplete();

            verify(repositorio).deletePending(GASTO_GUARDADO_ID, USER_ID);
        }

        @Test
        void unMovimientoInexistenteOAjenoEsNoEncontradoSinBorrarNada() {
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().reject(USER_ID, GASTO_GUARDADO_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                    .verify();

            verify(repositorio, never()).deletePending(any(), any());
        }

        @Test
        void unMovimientoYaConfirmadoEsUnConflictoYNoSeBorra() {
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(unGastoGuardado()));

            StepVerifier.create(useCase().reject(USER_ID, GASTO_GUARDADO_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "status",
                            ErrorCodes.INVALID_STATE))
                    .verify();

            verify(repositorio, never()).deletePending(any(), any());
        }

        @Test
        void siOtroRequestLoAproboOBorroEnMedioEsNoEncontrado() {
            when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                    .thenReturn(Mono.just(TransactionMother.unGastoPendiente()));
            when(repositorio.deletePending(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().reject(USER_ID, GASTO_GUARDADO_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "id", ErrorCodes.NOT_FOUND))
                    .verify();
        }
    }

    @Nested
    class RechazarLote {

        private static final UUID AJENO_ID = UUID.fromString("50000000-0000-7000-8000-0000000000ff");

        @Test
        void rechazaTodosLosPendientesDelLote() {
            List<UUID> ids = List.of(GASTO_GUARDADO_ID, TRANSFERENCIA_GUARDADA_ID);
            when(repositorio.findByIdsAndUser(ids, USER_ID))
                    .thenReturn(Flux.just(transferenciaPendiente(), TransactionMother.unGastoPendiente()));
            when(repositorio.deleteAllPending(ids, USER_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().rejectBatch(USER_ID, ids)).verifyComplete();

            verify(repositorio).deleteAllPending(ids, USER_ID);
        }

        @Test
        void unIdQueNoEsDelUsuarioEsNoEncontradoEnSuPosicionSinRechazarNinguno() {
            List<UUID> ids = List.of(GASTO_GUARDADO_ID, AJENO_ID);
            when(repositorio.findByIdsAndUser(ids, USER_ID)).thenReturn(Flux.just(TransactionMother.unGastoPendiente()));

            StepVerifier.create(useCase().rejectBatch(USER_ID, ids))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "ids[1]", ErrorCodes.NOT_FOUND))
                    .verify();

            verify(repositorio, never()).deleteAllPending(any(), any());
        }

        @Test
        void unMovimientoConfirmadoEsUnConflictoEnSuPosicionSinRechazarNinguno() {
            List<UUID> ids = List.of(GASTO_GUARDADO_ID, INGRESO_GUARDADO_ID);
            when(repositorio.findByIdsAndUser(ids, USER_ID))
                    .thenReturn(Flux.just(unIngresoGuardado(), TransactionMother.unGastoPendiente()));

            StepVerifier.create(useCase().rejectBatch(USER_ID, ids))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "ids[1]",
                            ErrorCodes.INVALID_STATE))
                    .verify();

            verify(repositorio, never()).deleteAllPending(any(), any());
        }

        @Test
        void conNoEncontradosYConfirmadosSaleSoloElNoEncontrado() {
            List<UUID> ids = List.of(AJENO_ID, INGRESO_GUARDADO_ID);
            when(repositorio.findByIdsAndUser(ids, USER_ID)).thenReturn(Flux.just(unIngresoGuardado()));

            StepVerifier.create(useCase().rejectBatch(USER_ID, ids))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "ids[0]", ErrorCodes.NOT_FOUND))
                    .verify();
        }

        @Test
        void cadaIdAusenteLlevaSuPropioError() {
            List<UUID> ids = List.of(GASTO_GUARDADO_ID, TRANSFERENCIA_GUARDADA_ID, AJENO_ID);
            when(repositorio.findByIdsAndUser(ids, USER_ID)).thenReturn(Flux.empty());

            StepVerifier.create(useCase().rejectBatch(USER_ID, ids))
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
            when(repositorio.findByIdsAndUser(ids, USER_ID)).thenReturn(Flux.just(TransactionMother.unGastoPendiente()));
            when(repositorio.deleteAllPending(ids, USER_ID)).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().rejectBatch(USER_ID, ids))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "ids", ErrorCodes.NOT_FOUND))
                    .verify();
        }

        private static Transaction transferenciaPendiente() {
            return TransactionMother.unaTransferenciaPendienteEntre(ORIGEN_ID, DESTINO_ID);
        }
    }

    @Nested
    class AprobarGrupo {

        /** Las cuentas y las categorias del usuario de TransactionMother, por id. */
        @BeforeEach
        void escenario() {
            for (Account cuenta : TransactionMother.cuentasDelUsuario()) {
                lenient().when(cuentas.findActiveByIdAndUser(cuenta.id(), USER_ID)).thenReturn(Mono.just(cuenta));
            }
            lenient().when(categorias.findActiveByIdAndUser(MERCADO_ID, USER_ID))
                    .thenReturn(Mono.just(TransactionMother.categoriasDelUsuario().getFirst()));
        }

        @Test
        void confirmaTodosLosPendientesYLosDevuelveConfirmadosEnElOrdenDelGrupo() {
            Transaction gasto = pendiente(TransactionMother.unGastoPendiente());
            Transaction transferencia = transferenciaPendienteDelGrupo();
            when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(gasto, transferencia));
            when(repositorio.confirmAll(List.of(GASTO_GUARDADO_ID, TRANSFERENCIA_GUARDADA_ID), USER_ID))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(useCase().approveGroup(USER_ID, GRUPO_ID))
                    .assertNext(t -> assertThat(t).isEqualTo(conEstado(gasto, TransactionStatus.CONFIRMED)))
                    .assertNext(t -> assertThat(t).isEqualTo(conEstado(transferencia, TransactionStatus.CONFIRMED)))
                    .verifyComplete();
        }

        /** FIN-101 */
        @Test
        void aprobarUnGrupoQueLlegoPorCorreoConservaElOrigenYElGrupo() {
            Transaction gasto = pendiente(
                    TransactionMother.conOrigen(TransactionMother.unGastoPendiente(), TransactionOrigin.EMAIL));
            when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(gasto));
            when(repositorio.confirmAll(List.of(GASTO_GUARDADO_ID), USER_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().approveGroup(USER_ID, GRUPO_ID))
                    .assertNext(t -> assertThat(t)
                            .extracting(Transaction::status, Transaction::origin, Transaction::groupId)
                            .containsExactly(TransactionStatus.CONFIRMED, TransactionOrigin.EMAIL, GRUPO_ID))
                    .verifyComplete();
        }

        @Test
        void soloConfirmaLosQueSiguenPendientes() {
            Transaction yaAprobado = TransactionMother.enGrupo(unGastoGuardado(), GRUPO_ID);
            Transaction transferencia = transferenciaPendienteDelGrupo();
            when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(yaAprobado, transferencia));
            when(repositorio.confirmAll(List.of(TRANSFERENCIA_GUARDADA_ID), USER_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().approveGroup(USER_ID, GRUPO_ID))
                    .assertNext(t -> assertThat(t.id()).isEqualTo(TRANSFERENCIA_GUARDADA_ID))
                    .verifyComplete();
        }

        @Test
        void unGrupoSinMovimientosDelUsuarioEsNoEncontradoSobreElGrupo() {
            when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.empty());

            StepVerifier.create(useCase().approveGroup(USER_ID, GRUPO_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "groupId",
                            ErrorCodes.NOT_FOUND))
                    .verify();

            verify(repositorio, never()).confirmAll(any(), any());
        }

        @Test
        void unGrupoYaConfirmadoEsUnConflictoSobreElEstado() {
            when(repositorio.findByGroup(GRUPO_ID, USER_ID))
                    .thenReturn(Flux.just(TransactionMother.enGrupo(unGastoGuardado(), GRUPO_ID)));

            StepVerifier.create(useCase().approveGroup(USER_ID, GRUPO_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "status",
                            ErrorCodes.INVALID_STATE))
                    .verify();

            verify(repositorio, never()).confirmAll(any(), any());
        }

        @Test
        void siUnoNoSePuedeAprobarNoSeConfirmaNinguno() {
            Transaction gasto = pendiente(TransactionMother.unGastoPendiente());
            Transaction enInactiva = pendiente(en(unaTransferenciaGuardada(), INACTIVA_ID, DESTINO_ID, "1000"));
            when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(gasto, enInactiva));

            StepVerifier.create(useCase().approveGroup(USER_ID, GRUPO_ID))
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

            StepVerifier.create(useCase().approveGroup(USER_ID, GRUPO_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "groupId",
                            ErrorCodes.NOT_FOUND))
                    .verify();
        }

        private static Transaction pendiente(Transaction t) {
            return TransactionMother.enGrupo(conEstado(t, TransactionStatus.PENDING), GRUPO_ID);
        }
    }

    @Nested
    class RechazarGrupo {

        @Test
        void borraSoloLosQueSiguenPendientes() {
            Transaction yaAprobado = TransactionMother.enGrupo(unGastoGuardado(), GRUPO_ID);
            when(repositorio.findByGroup(GRUPO_ID, USER_ID))
                    .thenReturn(Flux.just(yaAprobado, transferenciaPendienteDelGrupo()));
            when(repositorio.deletePendingIn(List.of(TRANSFERENCIA_GUARDADA_ID), USER_ID)).thenReturn(Mono.just(1L));

            StepVerifier.create(useCase().rejectGroup(USER_ID, GRUPO_ID)).verifyComplete();

            verify(repositorio).deletePendingIn(List.of(TRANSFERENCIA_GUARDADA_ID), USER_ID);
        }

        @Test
        void unGrupoSinMovimientosDelUsuarioEsNoEncontradoSobreElGrupo() {
            when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.empty());

            StepVerifier.create(useCase().rejectGroup(USER_ID, GRUPO_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "groupId",
                            ErrorCodes.NOT_FOUND))
                    .verify();

            verify(repositorio, never()).deletePendingIn(any(), any());
        }

        @Test
        void unGrupoYaConfirmadoEsUnConflictoSobreElEstadoSinBorrarNada() {
            when(repositorio.findByGroup(GRUPO_ID, USER_ID))
                    .thenReturn(Flux.just(TransactionMother.enGrupo(unGastoGuardado(), GRUPO_ID)));

            StepVerifier.create(useCase().rejectGroup(USER_ID, GRUPO_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, "status",
                            ErrorCodes.INVALID_STATE))
                    .verify();

            verify(repositorio, never()).deletePendingIn(any(), any());
        }

        @Test
        void siOtroRequestLosResolvioEnMedioEsNoEncontrado() {
            when(repositorio.findByGroup(GRUPO_ID, USER_ID)).thenReturn(Flux.just(transferenciaPendienteDelGrupo()));
            when(repositorio.deletePendingIn(List.of(TRANSFERENCIA_GUARDADA_ID), USER_ID)).thenReturn(Mono.just(0L));

            StepVerifier.create(useCase().rejectGroup(USER_ID, GRUPO_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, "groupId",
                            ErrorCodes.NOT_FOUND))
                    .verify();
        }
    }

    private TransactionUseCase useCase() {
        return new TransactionUseCase(repositorio, cuentas, categorias, cuentasDelUsuario, categoriasDelUsuario,
                monedas, tasas, alta, RELOJ);
    }

    private static Transaction transferenciaPendienteDelGrupo() {
        return TransactionMother.enGrupo(TransactionMother.unaTransferenciaPendienteEntre(ORIGEN_ID, DESTINO_ID),
                GRUPO_ID);
    }

    private static void esError(Throwable error, HttpStatus status, String campo, ErrorCodes codigo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors())
                .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                .containsExactly(tuple(campo, codigo.getCode()));
    }
}
