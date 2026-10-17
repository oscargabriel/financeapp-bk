package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.InstallmentMother.HOY;
import static com.oscargabriel.financeapp.core.support.InstallmentMother.unaCompra;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

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
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.core.domain.model.GroupScope;
import com.oscargabriel.financeapp.core.domain.model.InstallmentPurchase;
import com.oscargabriel.financeapp.core.domain.model.InstallmentRef;
import com.oscargabriel.financeapp.core.domain.model.ScheduledInstallment;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.model.UpdateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.InstallmentPurchaseRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.core.support.CurrencyMother;
import com.oscargabriel.financeapp.core.support.InstallmentMother;
import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class InstallmentPurchaseUseCaseTest {

    /** Viernes 9 de octubre a las 10:00 en Bogota. */
    private static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");

    private static final UUID USER_ID = TransactionMother.USER_ID;

    private static final UUID COMPRA_ID = InstallmentMother.COMPRA_ID;

    @Mock
    private AccountQueryPort cuentas;

    @Mock
    private CategoryQueryPort categorias;

    @Mock
    private CurrencyQueryPort monedas;

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private InstallmentPurchaseRepositoryPort compras;

    @Nested
    class Simular {

        @BeforeEach
        void escenario() {
            referenciasDelUsuario();
        }

        @Test
        void calculaLasCuotasConLaTasaDeLaTarjeta() {
            StepVerifier.create(useCase().preview(USER_ID, unaCompra().build()))
                    .assertNext(plan -> {
                        assertThat(plan.accountId()).isEqualTo(InstallmentMother.VISA_ID);
                        assertThat(plan.currencyCode()).isEqualTo("COP");
                        assertThat(plan.purchaseDate()).isEqualTo(HOY);
                        assertThat(plan.installmentCount()).isEqualTo(3);
                        assertThat(plan.monthlyInterestRate()).isEqualByComparingTo("2");
                        assertThat(plan.installments()).extracting(ScheduledInstallment::dueAt).containsExactly(
                                Instant.parse("2026-11-05T05:00:00Z"), Instant.parse("2026-12-05T05:00:00Z"),
                                Instant.parse("2027-01-05T05:00:00Z"));
                        assertThat(plan.installments()).extracting(ScheduledInstallment::amount).containsExactly(
                                new BigDecimal("424000"), new BigDecimal("416000"), new BigDecimal("408000"));
                        assertThat(plan.installments()).allSatisfy(c -> assertThat(c.transactionId()).isNull());
                        assertThat(plan.totalInterest()).isEqualByComparingTo("48000");
                        assertThat(plan.totalAmount()).isEqualByComparingTo("1248000");
                    })
                    .verifyComplete();
        }

        @Test
        void unaTarjetaQueCobraInteresAUnaCuotaLoCobraEnLaCuotaUnica() {
            CreateInstallmentPurchaseCommand compra = unaCompra()
                    .accountId(InstallmentMother.VISA_UNA_CUOTA_ID.toString()).amount("300000").installmentCount(1)
                    .build();

            StepVerifier.create(useCase().preview(USER_ID, compra))
                    .assertNext(plan -> {
                        assertThat(plan.installments()).singleElement().satisfies(c -> {
                            assertThat(c.interest()).isEqualByComparingTo("6000");
                            assertThat(c.amount()).isEqualByComparingTo("306000");
                        });
                        assertThat(plan.totalInterest()).isEqualByComparingTo("6000");
                    })
                    .verifyComplete();
        }

        @Test
        void unaTarjetaSinLaOpcionNoCobraInteresAUnaCuota() {
            CreateInstallmentPurchaseCommand compra = unaCompra().amount("300000").installmentCount(1).build();

            StepVerifier.create(useCase().preview(USER_ID, compra))
                    .assertNext(plan -> assertThat(plan.installments()).singleElement()
                            .satisfies(c -> assertThat(c.interest()).isEqualByComparingTo("0")))
                    .verifyComplete();
        }

        @Test
        void unaTarjetaSinTasaNoCobraInteres() {
            CreateInstallmentPurchaseCommand compra = unaCompra()
                    .accountId(InstallmentMother.VISA_SIN_TASA_ID.toString()).amount("900000").build();

            StepVerifier.create(useCase().preview(USER_ID, compra))
                    .assertNext(plan -> {
                        assertThat(plan.monthlyInterestRate()).isEqualByComparingTo("0");
                        assertThat(plan.installments()).allSatisfy(c -> {
                            assertThat(c.interest()).isEqualByComparingTo("0");
                            assertThat(c.amount()).isEqualByComparingTo("300000");
                        });
                    })
                    .verifyComplete();
        }

        @Test
        void unaCuentaQueNoEsTarjetaEsErrorSobreAccountId() {
            rechaza(unaCompra().accountId(TransactionMother.ORIGEN_ID.toString()), AHORA, errores ->
                    assertThat(errores).containsExactly(error(
                            "Las cuotas solo se registran sobre una tarjeta de credito", "accountId")));
        }

        @Test
        void unaTarjetaSinCorteOSinPagoEsErrorSobreAccountId() {
            String mensaje = "La tarjeta necesita dia de corte y dia de pago para calcular las cuotas";
            rechaza(unaCompra().accountId(InstallmentMother.VISA_SIN_CORTE_ID.toString()), AHORA, errores ->
                    assertThat(errores).containsExactly(error(mensaje, "accountId")));
            rechaza(unaCompra().accountId(InstallmentMother.VISA_SIN_PAGO_ID.toString()), AHORA, errores ->
                    assertThat(errores).containsExactly(error(mensaje, "accountId")));
        }

        /** FA-123: el plan va en la moneda de la tarjeta, a sus decimales. */
        @Test
        void unaTarjetaEnUsdCalculaLasCuotasEnCentavos() {
            CreateInstallmentPurchaseCommand compra = unaCompra().accountId(InstallmentMother.VISA_USD_ID.toString())
                    .amount("100").build();

            StepVerifier.create(useCase().preview(USER_ID, compra))
                    .assertNext(plan -> {
                        assertThat(plan.currencyCode()).isEqualTo("USD");
                        assertThat(plan.installments()).extracting(ScheduledInstallment::principal).containsExactly(
                                new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34"));
                        assertThat(plan.installments()).extracting(ScheduledInstallment::interest).containsExactly(
                                new BigDecimal("2.00"), new BigDecimal("1.33"), new BigDecimal("0.67"));
                        assertThat(plan.totalAmount()).isEqualByComparingTo("104");
                    })
                    .verifyComplete();
        }

        @Test
        void unaTarjetaEnUnaMonedaQueYaNoEstaActivaEsErrorSobreAccountId() {
            when(monedas.findActive()).thenReturn(Flux.just(CurrencyMother.cop()));

            rechaza(unaCompra().accountId(InstallmentMother.VISA_USD_ID.toString()), AHORA, errores ->
                    assertThat(errores).containsExactly(error("La moneda de la cuenta no esta activa", "accountId")));
        }

        @Test
        void unMontoQueNoAlcanzaParaUnPesoPorCuotaEsErrorSobreAmount() {
            rechaza(unaCompra().amount("10").installmentCount(12), AHORA, errores ->
                    assertThat(errores).containsExactly(
                            error("El monto tiene que alcanzar para al menos 1 COP de capital por cuota", "amount")));
        }

        @Test
        void unMontoQueNoAlcanzaParaUnCentavoPorCuotaEsErrorSobreAmount() {
            rechaza(unaCompra().accountId(InstallmentMother.VISA_USD_ID.toString()).amount("0.10")
                    .installmentCount(12), AHORA, errores -> assertThat(errores).containsExactly(
                            error("El monto tiene que alcanzar para al menos 0.01 USD de capital por cuota",
                                    "amount")));
        }

        @Test
        void unCentavoPorCuotaAlcanza() {
            CreateInstallmentPurchaseCommand compra = unaCompra()
                    .accountId(InstallmentMother.VISA_USD_SIN_TASA_ID.toString()).amount("0.12").installmentCount(12)
                    .build();

            StepVerifier.create(useCase().preview(USER_ID, compra))
                    .assertNext(plan -> assertThat(plan.installments()).hasSize(12)
                            .allSatisfy(c -> assertThat(c.amount()).isEqualByComparingTo("0.01")))
                    .verifyComplete();
        }

        @Test
        void unaTarjetaAjenaNoExiste() {
            rechaza(unaCompra().accountId(TransactionMother.AJENA_ID.toString()), AHORA, errores ->
                    assertThat(errores).containsExactly(error("La cuenta no existe", "accountId")));
        }

        @Test
        void unaCategoriaDeIngresosEsErrorSobreCategoryId() {
            rechaza(unaCompra().categoryId(TransactionMother.SALARIO_ID.toString()), AHORA, errores ->
                    assertThat(errores).extracting(ErrorDetail::getField).containsExactly("categoryId"));
        }

        @Test
        void unaCompraFuturaEnLaZonaDelUsuarioEsErrorSobrePurchaseDate() {
            rechaza(unaCompra().purchaseDate(HOY.plusDays(1)), AHORA, errores ->
                    assertThat(errores).containsExactly(
                            error("La fecha de compra no puede ser futura", "purchaseDate")));
        }

        /** A las 22:00 del 9 en Bogota ya es 10 en UTC: el 10 sigue siendo futuro para el usuario. */
        @Test
        void hoyEsElDiaDeLaZonaDelUsuario() {
            rechaza(unaCompra().purchaseDate(HOY.plusDays(1)), Instant.parse("2026-10-10T03:00:00Z"), errores ->
                    assertThat(errores).extracting(ErrorDetail::getField).containsExactly("purchaseDate"));
        }

        @Test
        void reportaTodosLosErroresJuntos() {
            rechaza(unaCompra().accountId(TransactionMother.ORIGEN_ID.toString()).purchaseDate(HOY.plusDays(1)),
                    AHORA, errores -> assertThat(errores).extracting(ErrorDetail::getField)
                            .containsExactlyInAnyOrder("accountId", "purchaseDate"));
        }

        private void rechaza(InstallmentMother.Compra compra, Instant ahora, Consumer<List<ErrorDetail>> errores) {
            StepVerifier.create(useCase(ahora).preview(USER_ID, compra.build()))
                    .expectErrorSatisfies(e -> {
                        assertThat(e).isInstanceOf(BadRequestException.class);
                        BadRequestException rechazo = (BadRequestException) e;
                        assertThat(rechazo.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        errores.accept(rechazo.getErrorResponse().getErrors());
                    })
                    .verify();
        }

        private static ErrorDetail error(String descripcion, String campo) {
            return ErrorDetail.of("VALIDATION_ERROR", descripcion, campo);
        }
    }

    @Nested
    class Crear {

        @Captor
        private ArgumentCaptor<InstallmentPurchase> compraGuardada;

        @Captor
        private ArgumentCaptor<List<Transaction>> cuotas;

        @BeforeEach
        void escenario() {
            referenciasDelUsuario();
            lenient().when(compras.save(any(), anyList())).thenReturn(Mono.empty());
        }

        @Test
        void guardaLaCompraConLaTasaDeLaTarjetaYSusCuotas() {
            StepVerifier.create(useCase().create(USER_ID, unaCompra().build())).expectNextCount(1).verifyComplete();

            verify(compras).save(compraGuardada.capture(), cuotas.capture());
            InstallmentPurchase compra = compraGuardada.getValue();
            assertThat(compra.id().version()).isEqualTo(7);
            assertThat(compra.userId()).isEqualTo(USER_ID);
            assertThat(compra.accountId()).isEqualTo(InstallmentMother.VISA_ID);
            assertThat(compra.categoryId()).isEqualTo(TransactionMother.MERCADO_ID);
            assertThat(compra.currencyCode()).isEqualTo("COP");
            assertThat(compra.description()).isEqualTo("Televisor");
            assertThat(compra.purchaseDate()).isEqualTo(HOY);
            assertThat(compra.installmentCount()).isEqualTo(3);
            assertThat(compra.monthlyInterestRate()).isEqualByComparingTo("2");

            assertThat(cuotas.getValue()).extracting(Transaction::occurredAt).containsExactly(
                    Instant.parse("2026-11-05T05:00:00Z"), Instant.parse("2026-12-05T05:00:00Z"),
                    Instant.parse("2027-01-05T05:00:00Z"));
            assertThat(cuotas.getValue()).extracting(Transaction::amount).containsExactly(
                    new BigDecimal("424000"), new BigDecimal("416000"), new BigDecimal("408000"));
            assertThat(cuotas.getValue()).extracting(Transaction::installment).containsExactly(
                    new InstallmentRef(compra.id(), 1, 3, new BigDecimal("400000")),
                    new InstallmentRef(compra.id(), 2, 3, new BigDecimal("400000")),
                    new InstallmentRef(compra.id(), 3, 3, new BigDecimal("400000")));
            assertThat(cuotas.getValue()).allSatisfy(t -> {
                assertThat(t.id().version()).isEqualTo(7);
                assertThat(t.userId()).isEqualTo(USER_ID);
                assertThat(t.type()).isEqualTo(TransactionType.EXPENSE);
                assertThat(t.accountId()).isEqualTo(InstallmentMother.VISA_ID);
                assertThat(t.destinationAccountId()).isNull();
                assertThat(t.categoryId()).isEqualTo(TransactionMother.MERCADO_ID);
                assertThat(t.currencyCode()).isEqualTo("COP");
                assertThat(t.description()).isEqualTo("Televisor");
                assertThat(t.notes()).isNull();
                assertThat(t.status()).isEqualTo(TransactionStatus.CONFIRMED);
                assertThat(t.origin()).isEqualTo(TransactionOrigin.WEB);
                assertThat(t.recurrenceId()).isNull();
            });
        }

        /** FA-123: la compra y sus cuotas van en la moneda de la tarjeta, redondeadas a sus decimales. */
        @Test
        void unaTarjetaEnUsdGuardaLaCompraYLasCuotasEnUsd() {
            StepVerifier.create(useCase().create(USER_ID, unaCompra()
                            .accountId(InstallmentMother.VISA_USD_ID.toString()).amount("100").build()))
                    .assertNext(creada -> assertThat(creada.view().remainingAmount()).isEqualByComparingTo("104"))
                    .verifyComplete();

            verify(compras).save(compraGuardada.capture(), cuotas.capture());
            assertThat(compraGuardada.getValue().currencyCode()).isEqualTo("USD");
            assertThat(cuotas.getValue()).extracting(Transaction::amount).containsExactly(
                    new BigDecimal("35.33"), new BigDecimal("34.66"), new BigDecimal("34.01"));
            assertThat(cuotas.getValue()).allSatisfy(t -> assertThat(t.currencyCode()).isEqualTo("USD"));
        }

        @Test
        void unaCompraAUnaCuotaCobraInteresSiLaTarjetaLoCobra() {
            StepVerifier.create(useCase().create(USER_ID, unaCompra()
                            .accountId(InstallmentMother.VISA_UNA_CUOTA_ID.toString()).amount("300000")
                            .installmentCount(1).build()))
                    .assertNext(creada -> assertThat(creada.view().remainingAmount())
                            .isEqualByComparingTo("306000"))
                    .verifyComplete();

            verify(compras).save(compraGuardada.capture(), cuotas.capture());
            assertThat(cuotas.getValue()).extracting(Transaction::amount).containsExactly(new BigDecimal("306000"));
        }

        @Test
        void devuelveLaCompraConSuPlanYElResumen() {
            StepVerifier.create(useCase().create(USER_ID, unaCompra().build()))
                    .assertNext(creada -> {
                        verify(compras).save(compraGuardada.capture(), cuotas.capture());
                        List<Transaction> guardadas = cuotas.getValue();
                        assertThat(creada.view().purchase()).isEqualTo(compraGuardada.getValue());
                        assertThat(creada.view().paidCount()).isZero();
                        assertThat(creada.view().remainingPrincipal()).isEqualByComparingTo("1200000");
                        assertThat(creada.view().remainingAmount()).isEqualByComparingTo("1248000");
                        assertThat(creada.view().nextInstallment().number()).isEqualTo(1);
                        assertThat(creada.view().nextInstallment().transactionId())
                                .isEqualTo(guardadas.getFirst().id());
                        assertThat(creada.view().nextInstallment().dueAt())
                                .isEqualTo(Instant.parse("2026-11-05T05:00:00Z"));
                        assertThat(creada.view().nextInstallment().amount()).isEqualByComparingTo("424000");
                        assertThat(creada.installments()).extracting(ScheduledInstallment::transactionId)
                                .containsExactlyElementsOf(guardadas.stream().map(Transaction::id).toList());
                        assertThat(creada.installments()).extracting(ScheduledInstallment::interest).containsExactly(
                                new BigDecimal("24000"), new BigDecimal("16000"), new BigDecimal("8000"));
                    })
                    .verifyComplete();
        }

        /** Del 10 de junio, a 6 cuotas: julio, agosto, septiembre y octubre ya vencieron. */
        @Test
        void unaCompraPasadaYaTienePagadasLasVencidas() {
            StepVerifier.create(useCase().create(USER_ID, unaCompra().amount("600000").installmentCount(6)
                            .purchaseDate(LocalDate.of(2026, 6, 10)).build()))
                    .assertNext(creada -> {
                        assertThat(creada.view().paidCount()).isEqualTo(4);
                        assertThat(creada.view().remainingPrincipal()).isEqualByComparingTo("200000");
                        assertThat(creada.view().remainingAmount()).isEqualByComparingTo("206000");
                        assertThat(creada.view().nextInstallment().number()).isEqualTo(5);
                    })
                    .verifyComplete();
        }

        @Test
        void unaCompraYaPagadaNoTieneProximaCuota() {
            StepVerifier.create(useCase().create(USER_ID, unaCompra().purchaseDate(LocalDate.of(2026, 1, 10))
                            .build()))
                    .assertNext(creada -> {
                        assertThat(creada.view().paidCount()).isEqualTo(3);
                        assertThat(creada.view().remainingAmount()).isEqualByComparingTo("0");
                        assertThat(creada.view().nextInstallment()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        void unErrorNoGuardaNada() {
            StepVerifier.create(useCase().create(USER_ID, unaCompra()
                            .accountId(TransactionMother.ORIGEN_ID.toString()).build()))
                    .expectError(BadRequestException.class)
                    .verify();

            verify(compras, never()).save(any(), anyList());
        }
    }

    @Nested
    class Listar {

        @Test
        void devuelveLasActivasDelUsuarioEnElOrdenDelRepositorio() {
            when(compras.findActiveByUser(TransactionMother.USER_ID))
                    .thenReturn(Flux.just(InstallmentMother.televisorSinPagar()));

            StepVerifier.create(useCase().list(TransactionMother.USER_ID))
                    .expectNext(InstallmentMother.televisorSinPagar())
                    .verifyComplete();
        }
    }

    @Nested
    class Actualizar {

        @BeforeEach
        void escenario() {
            lenient().when(cuentas.findByUser(USER_ID, true))
                    .thenReturn(Flux.fromIterable(InstallmentMother.cuentasConTarjetas()));
            lenient().when(categorias.findActiveByUser(eq(USER_ID), any()))
                    .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
            lenient().when(compras.update(any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(true));
            lenient().when(compras.findByIdAndUser(COMPRA_ID, USER_ID))
                    .thenReturn(Mono.just(InstallmentMother.televisorSinPagar()));
        }

        @Test
        void aplicaElAlcanceConLaDescripcionRecortadaYDevuelveLaCompraLeida() {
            StepVerifier.create(useCase().update(USER_ID, COMPRA_ID,
                            new UpdateInstallmentPurchaseCommand(GroupScope.FUTURE, "  TV sala ", null)))
                    .expectNext(InstallmentMother.televisorSinPagar())
                    .verifyComplete();

            verify(compras).update(eq(COMPRA_ID), eq(USER_ID), eq(GroupScope.FUTURE), eq(AHORA), eq("TV sala"),
                    isNull());
        }

        @Test
        void cambiaLaCategoriaEnTodas() {
            StepVerifier.create(useCase().update(USER_ID, COMPRA_ID,
                            new UpdateInstallmentPurchaseCommand(GroupScope.ALL, null,
                                    TransactionMother.AMBAS_ID.toString())))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(compras).update(eq(COMPRA_ID), eq(USER_ID), eq(GroupScope.ALL), eq(AHORA), isNull(),
                    eq(TransactionMother.AMBAS_ID));
        }

        @Test
        void unaCategoriaDeIngresosEsErrorSobreCategoryIdYNoEscribe() {
            StepVerifier.create(useCase().update(USER_ID, COMPRA_ID,
                            new UpdateInstallmentPurchaseCommand(GroupScope.ALL, null,
                                    TransactionMother.SALARIO_ID.toString())))
                    .expectErrorSatisfies(e -> {
                        BadRequestException rechazo = (BadRequestException) e;
                        assertThat(rechazo.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rechazo.getErrorResponse().getErrors().getFirst().getField())
                                .isEqualTo("categoryId");
                    })
                    .verify();

            verify(compras, never()).update(any(), any(), any(), any(), any(), any());
        }

        @Test
        void sinCompraQueEditarEs404SobreId() {
            when(compras.update(any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().update(USER_ID, COMPRA_ID,
                            new UpdateInstallmentPurchaseCommand(GroupScope.ALL, "TV", null)))
                    .expectErrorSatisfies(e -> {
                        BadRequestException rechazo = (BadRequestException) e;
                        assertThat(rechazo.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(rechazo.getErrorResponse().getErrors().getFirst().getCode())
                                .isEqualTo("NOT_FOUND");
                        assertThat(rechazo.getErrorResponse().getErrors().getFirst().getField()).isEqualTo("id");
                    })
                    .verify();
        }

        @Test
        void siLaCancelaronEnMedioTambienEs404() {
            when(compras.findByIdAndUser(COMPRA_ID, USER_ID)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().update(USER_ID, COMPRA_ID,
                            new UpdateInstallmentPurchaseCommand(GroupScope.ALL, "TV", null)))
                    .expectErrorSatisfies(e -> assertThat(((BadRequestException) e).getHttpStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND))
                    .verify();
        }

        @Test
        void soloDescripcionNoNecesitaLasCategorias() {
            StepVerifier.create(useCase().update(USER_ID, COMPRA_ID,
                            new UpdateInstallmentPurchaseCommand(GroupScope.ALL, "TV", null)))
                    .expectNextCount(1)
                    .verifyComplete();

            verifyNoInteractions(cuentas, categorias);
        }
    }

    @Nested
    class Cancelar {

        @Test
        void cancelaConElInstanteDelReloj() {
            when(compras.cancel(COMPRA_ID, USER_ID, AHORA)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().cancel(USER_ID, COMPRA_ID)).verifyComplete();
        }

        @Test
        void sinCompraQueCancelarEs404() {
            when(compras.cancel(COMPRA_ID, USER_ID, AHORA)).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().cancel(USER_ID, COMPRA_ID))
                    .expectErrorSatisfies(e -> {
                        BadRequestException rechazo = (BadRequestException) e;
                        assertThat(rechazo.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(rechazo.getErrorResponse().getErrors().getFirst().getDescription())
                                .isEqualTo("La compra no existe");
                    })
                    .verify();
        }
    }

    /** Las tarjetas, categorias, monedas y la zona que la simulacion y el alta leen del usuario. */
    private void referenciasDelUsuario() {
        lenient().when(cuentas.findByUser(USER_ID, true))
                .thenReturn(Flux.fromIterable(InstallmentMother.cuentasConTarjetas()));
        lenient().when(categorias.findActiveByUser(eq(USER_ID), any()))
                .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
        lenient().when(monedas.findActive()).thenReturn(Flux.just(CurrencyMother.cop(), CurrencyMother.usd()));
        lenient().when(usuarios.findActiveProfile(USER_ID)).thenReturn(Mono.just(new UserProfile(USER_ID,
                "ana@ejemplo.com", "Ana", null, null, "COP", "America/Bogota", true, false, false)));
    }

    private InstallmentPurchaseUseCase useCase() {
        return useCase(AHORA);
    }

    private InstallmentPurchaseUseCase useCase(Instant ahora) {
        return new InstallmentPurchaseUseCase(cuentas, categorias, monedas, usuarios, compras,
                Clock.fixed(ahora, ZoneOffset.UTC));
    }
}
