package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.assertj.core.groups.Tuple;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.ExchangeRate;
import com.oscargabriel.financeapp.domain.model.OriginalAmount;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.support.CurrencyMother;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateTransactionsUseCaseTest {

    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-09-26T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private AccountQueryPort cuentas;

    @Mock
    private CategoryQueryPort categorias;

    @Mock
    private CurrencyQueryPort monedas;

    @Mock
    private TransactionRepositoryPort repositorio;

    @Mock
    private ResolveExchangeRatePort tasas;

    @Captor
    private ArgumentCaptor<List<Transaction>> guardadas;

    @BeforeEach
    void escenario() {
        when(cuentas.findByUser(TransactionMother.USER_ID, true))
                .thenReturn(Flux.fromIterable(TransactionMother.cuentasDelUsuario()));
        when(categorias.findActiveByUser(eq(TransactionMother.USER_ID), any()))
                .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
        when(repositorio.saveAll(anyList()))
                .thenAnswer(invocacion -> Flux.fromIterable(invocacion.<List<Transaction>>getArgument(0)));
        when(tasas.refreshToday(any())).thenReturn(Mono.empty());
        when(monedas.findActive()).thenReturn(Flux.just(CurrencyMother.cop(), CurrencyMother.usd()));
    }

    @Test
    void guardaElLoteEnOrdenYLoDevuelveEnElMismoOrden() {
        List<CreateTransactionCommand> lote = List.of(
                TransactionMother.unIngreso().build(),
                TransactionMother.unaTransferencia().build(),
                TransactionMother.unGasto().build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, lote))
                .assertNext(t -> assertThat(t.type()).isEqualTo(TransactionType.INCOME))
                .assertNext(t -> assertThat(t.type()).isEqualTo(TransactionType.TRANSFER))
                .assertNext(t -> assertThat(t.type()).isEqualTo(TransactionType.EXPENSE))
                .verifyComplete();

        verify(repositorio).saveAll(guardadas.capture());
        assertThat(guardadas.getValue()).extracting(Transaction::description)
                .containsExactly("Pago quincena", "Ahorro del mes", "Mercado de la semana");
    }

    @Test
    void unGastoQuedaConSuCategoriaEnCopYElInstanteDeLaFecha() {
        CreateTransactionCommand gasto = TransactionMother.unGasto()
                .description("  Mercado  ").notes("Pagado en efectivo").currencyCode("cop").build();

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, List.of(gasto)))
                .assertNext(t -> {
                    assertThat(t.id().version()).isEqualTo(7);
                    assertThat(t.userId()).isEqualTo(TransactionMother.USER_ID);
                    assertThat(t.accountId()).isEqualTo(TransactionMother.ORIGEN_ID);
                    assertThat(t.categoryId()).isEqualTo(TransactionMother.MERCADO_ID);
                    assertThat(t.destinationAccountId()).isNull();
                    assertThat(t.amount()).isEqualByComparingTo("50000");
                    assertThat(t.currencyCode()).isEqualTo("COP");
                    assertThat(t.description()).isEqualTo("Mercado");
                    assertThat(t.notes()).isEqualTo("Pagado en efectivo");
                    assertThat(t.occurredAt()).isEqualTo(Instant.parse("2026-09-20T15:15:00Z"));
                })
                .verifyComplete();
    }

    @Test
    void todoLoQueEntraPorLaWebQuedaConfirmadoYConSuOrigen() {
        List<CreateTransactionCommand> lote = List.of(TransactionMother.unGasto().build(),
                TransactionMother.unIngreso().build(), TransactionMother.unaTransferencia().build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, lote))
                .assertNext(t -> assertThat(t).extracting(Transaction::status, Transaction::origin)
                        .containsExactly(TransactionStatus.CONFIRMED, TransactionOrigin.WEB))
                .assertNext(t -> assertThat(t).extracting(Transaction::status, Transaction::origin)
                        .containsExactly(TransactionStatus.CONFIRMED, TransactionOrigin.WEB))
                .assertNext(t -> assertThat(t).extracting(Transaction::status, Transaction::origin)
                        .containsExactly(TransactionStatus.CONFIRMED, TransactionOrigin.WEB))
                .verifyComplete();
    }

    @Test
    void loQueRegistraElAsistenteQuedaPendienteYConOrigenTelegram() {
        List<CreateTransactionCommand> lote = List.of(TransactionMother.unGasto().build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.TELEGRAM, lote))
                .assertNext(t -> assertThat(t).extracting(Transaction::status, Transaction::origin)
                        .containsExactly(TransactionStatus.PENDING, TransactionOrigin.TELEGRAM))
                .verifyComplete();
    }

    @Test
    void unaTransferenciaLlevaDestinoYNoLlevaCategoria() {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB,
                        List.of(TransactionMother.unaTransferencia().build())))
                .assertNext(t -> {
                    assertThat(t.destinationAccountId()).isEqualTo(TransactionMother.DESTINO_ID);
                    assertThat(t.categoryId()).isNull();
                })
                .verifyComplete();
    }

    @ParameterizedTest(name = "occurredAt = [{0}]")
    @NullSource
    @ValueSource(strings = {"", "   "})
    void unElementoSinFechaQuedaConElInstanteDelReloj(String fecha) {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB,
                        List.of(TransactionMother.unGasto().occurredAt(fecha).build())))
                .assertNext(t -> assertThat(t.occurredAt()).isEqualTo(RELOJ.instant()))
                .verifyComplete();
    }

    /**
     * Con un reloj que avanza en cada lectura, leerlo por elemento daria tres instantes distintos: la
     * prueba es que se lee una vez por lote.
     */
    @Test
    void losElementosSinFechaDelLoteCompartenElMismoInstante() {
        Clock queAvanza = new RelojQueAvanza(RELOJ.instant());
        List<CreateTransactionCommand> lote = List.of(
                TransactionMother.unGasto().occurredAt(null).build(),
                TransactionMother.unIngreso().occurredAt(null).build(),
                TransactionMother.unGasto().occurredAt(null).build());

        StepVerifier.create(new CreateTransactionsUseCase(cuentas, categorias, monedas, repositorio, tasas, queAvanza)
                        .create(TransactionMother.USER_ID, TransactionOrigin.WEB, lote).map(Transaction::occurredAt).distinct())
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void unLoteMixtoConservaLaFechaExplicita() {
        List<CreateTransactionCommand> lote = List.of(
                TransactionMother.unGasto().build(),
                TransactionMother.unGasto().occurredAt(null).build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, lote))
                .assertNext(t -> assertThat(t.occurredAt()).isEqualTo(Instant.parse("2026-09-20T15:15:00Z")))
                .assertNext(t -> assertThat(t.occurredAt()).isEqualTo(RELOJ.instant()))
                .verifyComplete();
    }

    @Test
    void unaCategoriaDeAmbosAlcancesSirveParaGastoEIngreso() {
        List<CreateTransactionCommand> lote = List.of(
                TransactionMother.unGasto().categoryId(TransactionMother.AMBAS_ID.toString()).build(),
                TransactionMother.unIngreso().categoryId(TransactionMother.AMBAS_ID.toString()).build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, lote))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void consultaLasCategoriasDeLosTresAlcances() {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB,
                        List.of(TransactionMother.unGasto().build())))
                .expectNextCount(1)
                .verifyComplete();

        verify(categorias).findActiveByUser(TransactionMother.USER_ID, EnumSet.allOf(CategoryScope.class));
    }

    @Test
    void cadaMovimientoRecibeSuPropioId() {
        List<CreateTransactionCommand> lote = Collections.nCopies(3, TransactionMother.unGasto().build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, lote).map(Transaction::id).distinct())
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    void aceptaElTopeDeQuinientos() {
        List<CreateTransactionCommand> lote = Collections.nCopies(500, TransactionMother.unGasto().build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, lote))
                .expectNextCount(500)
                .verifyComplete();
    }

    /**
     * Lo que se decide con las cuentas y categorias del usuario. El formato de cada elemento lo prueba
     * CreateTransactionRequestTest, y el tamano del lote y los elementos nulos TransactionControllerTest.
     */
    static Stream<Arguments> unaReferenciaInvalida() {
        String ajena = TransactionMother.AJENA_ID.toString();
        String mercado = TransactionMother.MERCADO_ID.toString();
        String salario = TransactionMother.SALARIO_ID.toString();
        return Stream.of(
                Arguments.of("cuenta que no es UUID", TransactionMother.unGasto().accountId("abc"), "accountId"),
                Arguments.of("cuenta ajena", TransactionMother.unGasto().accountId(ajena), "accountId"),
                Arguments.of("cuenta desactivada",
                        TransactionMother.unGasto().accountId(TransactionMother.INACTIVA_ID.toString()),
                        "accountId"),
                Arguments.of("categoria que no es UUID", TransactionMother.unGasto().categoryId("x"), "categoryId"),
                Arguments.of("categoria ajena", TransactionMother.unGasto().categoryId(ajena), "categoryId"),
                Arguments.of("gasto con categoria de ingreso",
                        TransactionMother.unGasto().categoryId(salario), "categoryId"),
                Arguments.of("ingreso con categoria de gasto",
                        TransactionMother.unIngreso().categoryId(mercado), "categoryId"),
                Arguments.of("transferencia a cuenta ajena",
                        TransactionMother.unaTransferencia().destinationAccountId(ajena), "destinationAccountId"),
                Arguments.of("transferencia a cuenta desactivada",
                        TransactionMother.unaTransferencia()
                                .destinationAccountId(TransactionMother.INACTIVA_ID.toString()),
                        "destinationAccountId"),
                Arguments.of("moneda que no esta activa en el catalogo",
                        TransactionMother.unGasto().currencyCode("XYZ"), "currencyCode"),
                Arguments.of("monto de destino entre dos cuentas en COP",
                        TransactionMother.unaTransferencia().destinationAmount(BigDecimal.TEN), "destinationAmount"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unaReferenciaInvalida")
    void rechazaLaReferenciaConSuIndiceSinGuardarNada(String caso, TransactionMother.Elemento elemento,
            String campo) {
        List<CreateTransactionCommand> lote = List.of(TransactionMother.unIngreso().build(), elemento.build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, lote))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField)
                            .containsExactly("[1]." + campo);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getCode)
                            .containsOnly(ErrorCodes.VALIDATION_ERROR.getCode());
                })
                .verify();

        verify(repositorio, never()).saveAll(any());
    }

    @Test
    void reportaTodosLosErroresDeTodosLosElementosJuntos() {
        List<CreateTransactionCommand> lote = List.of(
                TransactionMother.unGasto().build(),
                TransactionMother.unGasto().accountId(TransactionMother.AJENA_ID.toString()).build(),
                TransactionMother.unaTransferencia().destinationAccountId(TransactionMother.INACTIVA_ID.toString()).build(),
                TransactionMother.unIngreso().categoryId(TransactionMother.MERCADO_ID.toString()).build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, lote))
                .expectErrorSatisfies(error -> assertThat(((BadRequestException) error)
                        .getErrorResponse().getErrors())
                        .extracting(ErrorDetail::getField)
                        .containsExactly("[1].accountId", "[2].destinationAccountId", "[3].categoryId"))
                .verify();

        verify(repositorio, never()).saveAll(any());
    }

    /** La descripcion se guarda recortada: los espacios del borde no son parte de ella. */
    @Test
    void guardaLaDescripcionRecortada() {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB,
                        List.of(TransactionMother.unGasto().description("  Mercado  ").build())))
                .assertNext(movimiento -> assertThat(movimiento.description()).isEqualTo("Mercado"))
                .verifyComplete();
    }

    @Test
    void propagaElFalloDelRepositorio() {
        when(repositorio.saveAll(anyList())).thenReturn(Flux.error(new IllegalStateException("se cayo la base")));

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB,
                        List.of(TransactionMother.unGasto().build())))
                .expectErrorMessage("se cayo la base")
                .verify();
    }

    @Test
    void noHaceNadaHastaQueAlguienSeSuscribe() {
        useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, List.of(TransactionMother.unGasto().build()));

        verifyNoInteractions(cuentas, categorias, repositorio);
    }

    /** FA-122: la tasa del dia se pide antes de guardar, para que el equivalente en USD de lo reciente no quede viejo. */
    @Test
    void pideLaTasaDeHoyAntesDeGuardarCuandoHayUnElementoDeHoy() {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB,
                        List.of(TransactionMother.unGasto().build(), TransactionMother.unGasto().occurredAt(null).build())))
                .expectNextCount(2)
                .verifyComplete();

        InOrder orden = inOrder(tasas, repositorio);
        orden.verify(tasas).refreshToday(Set.of("COP"));
        orden.verify(repositorio).saveAll(anyList());
    }

    @Test
    void pideLaTasaDeHoyCuandoHayUnElementoProgramado() {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB,
                        List.of(TransactionMother.unGasto().occurredAt("2026-10-15T10:00:00-05:00").build())))
                .expectNextCount(1)
                .verifyComplete();

        verify(tasas).refreshToday(Set.of("COP"));
    }

    @Test
    void noPideLaTasaDeHoyCuandoTodoEsPasado() {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB,
                        List.of(TransactionMother.unGasto().build())))
                .expectNextCount(1)
                .verifyComplete();

        verifyNoInteractions(tasas);
    }

    @Test
    void noPideLaTasaDeHoySiElLoteEsInvalido() {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB,
                        List.of(TransactionMother.unGasto().occurredAt(null).accountId("no-es-un-uuid").build())))
                .expectError(BadRequestException.class)
                .verify();

        verifyNoInteractions(tasas);
    }

    /** FA-51: el movimiento se registra en la moneda de su cuenta. */
    @Test
    void unGastoEnUnaCuentaEnUsdQuedaEnUsdSinConvertir() {
        CreateTransactionCommand gasto = TransactionMother.unGasto().accountId(TransactionMother.USD_ID.toString())
                .amount(new BigDecimal("25")).currencyCode(" usd ").build();

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, List.of(gasto)))
                .assertNext(t -> {
                    assertThat(t.currencyCode()).isEqualTo("USD");
                    assertThat(t.amount()).isEqualByComparingTo("25");
                    assertThat(t.status()).isEqualTo(TransactionStatus.CONFIRMED);
                    assertThat(t.original()).isNull();
                    assertThat(t.destinationAmount()).isNull();
                })
                .verifyComplete();

        verify(tasas, never()).resolve(any(), any(), any());
    }

    @Test
    void unGastoEnOtraMonedaSeConvierteALaDeLaCuentaYQuedaPendiente() {
        when(tasas.resolve("COP", "USD", DIA)).thenReturn(Mono.just(tasa("COP", "USD", "0.0002439024")));
        CreateTransactionCommand gasto = TransactionMother.unGasto().accountId(TransactionMother.USD_ID.toString())
                .amount(new BigDecimal("41000")).currencyCode("cop").build();

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, List.of(gasto)))
                .assertNext(t -> {
                    assertThat(t.currencyCode()).isEqualTo("USD");
                    assertThat(t.amount()).isEqualTo(new BigDecimal("10.00"));
                    assertThat(t.status()).isEqualTo(TransactionStatus.PENDING);
                    assertThat(t.origin()).isEqualTo(TransactionOrigin.WEB);
                    assertThat(t.original()).isEqualTo(new OriginalAmount(new BigDecimal("41000"), "COP"));
                })
                .verifyComplete();
    }

    @Test
    void elConvertidoSeRedondeaALosDecimalesDeLaMonedaDeLaCuenta() {
        when(tasas.resolve("USD", "COP", DIA)).thenReturn(Mono.just(tasa("USD", "COP", "4100.5")));
        CreateTransactionCommand gasto = TransactionMother.unGasto().amount(new BigDecimal("10")).currencyCode("USD")
                .build();

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, List.of(gasto)))
                .assertNext(t -> assertThat(t.amount()).isEqualTo(new BigDecimal("41005")))
                .verifyComplete();
    }

    @Test
    void unaTransferenciaEntreMonedasConMontoDeDestinoQuedaConfirmadaSinConsultarTasas() {
        CreateTransactionCommand transferencia = TransactionMother.unaTransferencia()
                .destinationAccountId(TransactionMother.USD_ID.toString()).amount(new BigDecimal("410000"))
                .destinationAmount(new BigDecimal("95")).build();

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, List.of(transferencia)))
                .assertNext(t -> {
                    assertThat(t.status()).isEqualTo(TransactionStatus.CONFIRMED);
                    assertThat(t.currencyCode()).isEqualTo("COP");
                    assertThat(t.destinationAmount()).isEqualByComparingTo("95");
                    assertThat(t.original()).isNull();
                })
                .verifyComplete();

        verify(tasas, never()).resolve(any(), any(), any());
    }

    @Test
    void unaTransferenciaEntreMonedasSinMontoDeDestinoLoCalculaYQuedaPendiente() {
        when(tasas.resolve("USD", "COP", DIA)).thenReturn(Mono.just(tasa("USD", "COP", "4100")));
        CreateTransactionCommand transferencia = TransactionMother.unaTransferencia()
                .accountId(TransactionMother.USD_ID.toString()).destinationAccountId(TransactionMother.ORIGEN_ID.toString())
                .amount(new BigDecimal("10")).build();

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, List.of(transferencia)))
                .assertNext(t -> {
                    assertThat(t.currencyCode()).isEqualTo("USD");
                    assertThat(t.amount()).isEqualByComparingTo("10");
                    assertThat(t.destinationAmount()).isEqualTo(new BigDecimal("41000"));
                    assertThat(t.status()).isEqualTo(TransactionStatus.PENDING);
                    assertThat(t.original()).isNull();
                })
                .verifyComplete();
    }

    /** Diez dolares pagados desde la cuenta en pesos: el origen se convierte, y el destino sale del convertido. */
    @Test
    void unaTransferenciaRecibidaEnLaMonedaDelDestinoConvierteLasDosPatas() {
        when(tasas.resolve("USD", "COP", DIA)).thenReturn(Mono.just(tasa("USD", "COP", "4100")));
        when(tasas.resolve("COP", "USD", DIA)).thenReturn(Mono.just(tasa("COP", "USD", "0.0002439024")));
        CreateTransactionCommand transferencia = TransactionMother.unaTransferencia()
                .destinationAccountId(TransactionMother.USD_ID.toString()).amount(new BigDecimal("10"))
                .currencyCode("USD").build();

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, List.of(transferencia)))
                .assertNext(t -> {
                    assertThat(t.currencyCode()).isEqualTo("COP");
                    assertThat(t.amount()).isEqualTo(new BigDecimal("41000"));
                    assertThat(t.original()).isEqualTo(new OriginalAmount(new BigDecimal("10"), "USD"));
                    assertThat(t.destinationAmount()).isEqualTo(new BigDecimal("10.00"));
                    assertThat(t.status()).isEqualTo(TransactionStatus.PENDING);
                })
                .verifyComplete();
    }

    @Test
    void pideCadaTasaUnaVezPorParYFechaEnElLote() {
        when(tasas.resolve("COP", "USD", DIA)).thenReturn(Mono.just(tasa("COP", "USD", "0.0002439024")));
        CreateTransactionCommand gasto = TransactionMother.unGasto().accountId(TransactionMother.USD_ID.toString())
                .currencyCode("COP").build();

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, List.of(gasto, gasto, gasto)))
                .expectNextCount(3)
                .verifyComplete();

        verify(tasas, times(1)).resolve("COP", "USD", DIA);
    }

    @Test
    void sinNingunaTasaDelParRespondeElServicioExternoSinGuardarNada() {
        when(tasas.resolve("COP", "USD", DIA)).thenReturn(Mono.error(new BadRequestException(HttpStatus.NOT_FOUND,
                ErrorCodes.NOT_FOUND, "No hay ninguna tasa de cambio de COP a USD", "date")));
        CreateTransactionCommand gasto = TransactionMother.unGasto().accountId(TransactionMother.USD_ID.toString())
                .currencyCode("COP").build();

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, TransactionOrigin.WEB, List.of(gasto)))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(bre.getErrorResponse().getErrors()).extracting(ErrorDetail::getCode, ErrorDetail::getField)
                            .containsExactly(Tuple.tuple(ErrorCodes.EXTERNAL_SERVICE_ERROR.getCode(), "server"));
                })
                .verify();

        verify(repositorio, never()).saveAll(any());
    }

    private static final LocalDate DIA = LocalDate.of(2026, 9, 20);

    private static ExchangeRate tasa(String desde, String hacia, String valor) {
        return new ExchangeRate(desde, hacia, DIA, new BigDecimal(valor), DIA);
    }

    private CreateTransactionsUseCase useCase() {
        return new CreateTransactionsUseCase(cuentas, categorias, monedas, repositorio, tasas, RELOJ);
    }

    /** Avanza un milisegundo en cada lectura. */
    private static final class RelojQueAvanza extends Clock {

        private Instant ahora;

        RelojQueAvanza(Instant inicio) {
            this.ahora = inicio;
        }

        @Override
        public Instant instant() {
            Instant leido = ahora;
            ahora = ahora.plus(1, ChronoUnit.MILLIS);
            return leido;
        }

        @Override
        public ZoneId getZone() {
            return RELOJ.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
