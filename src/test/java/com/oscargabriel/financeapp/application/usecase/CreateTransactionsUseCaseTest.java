package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
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
    private TransactionRepositoryPort repositorio;

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
    }

    @Test
    void guardaElLoteEnOrdenYLoDevuelveEnElMismoOrden() {
        List<CreateTransactionCommand> lote = List.of(
                TransactionMother.unIngreso().build(),
                TransactionMother.unaTransferencia().build(),
                TransactionMother.unGasto().build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, lote))
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

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, List.of(gasto)))
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
    void unaTransferenciaLlevaDestinoYNoLlevaCategoria() {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID,
                        List.of(TransactionMother.unaTransferencia().build())))
                .assertNext(t -> {
                    assertThat(t.destinationAccountId()).isEqualTo(TransactionMother.DESTINO_ID);
                    assertThat(t.categoryId()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void unaCategoriaDeAmbosAlcancesSirveParaGastoEIngreso() {
        List<CreateTransactionCommand> lote = List.of(
                TransactionMother.unGasto().categoryId(TransactionMother.AMBAS_ID.toString()).build(),
                TransactionMother.unIngreso().categoryId(TransactionMother.AMBAS_ID.toString()).build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, lote))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void consultaLasCategoriasDeLosTresAlcances() {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID,
                        List.of(TransactionMother.unGasto().build())))
                .expectNextCount(1)
                .verifyComplete();

        verify(categorias).findActiveByUser(TransactionMother.USER_ID, EnumSet.allOf(CategoryScope.class));
    }

    @Test
    void cadaMovimientoRecibeSuPropioId() {
        List<CreateTransactionCommand> lote = Collections.nCopies(3, TransactionMother.unGasto().build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, lote).map(Transaction::id).distinct())
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    void aceptaElTopeDeQuinientos() {
        List<CreateTransactionCommand> lote = Collections.nCopies(500, TransactionMother.unGasto().build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, lote))
                .expectNextCount(500)
                .verifyComplete();
    }

    static Stream<Arguments> lotesFueraDeTamano() {
        List<CreateTransactionCommand> quinientosUno = new ArrayList<>(
                Collections.nCopies(501, TransactionMother.unGasto().type(null).build()));
        return Stream.of(
                Arguments.of("vacio", List.of()),
                Arguments.of("nulo", null),
                Arguments.of("de 501", quinientosUno));
    }

    @ParameterizedTest(name = "lote {0}")
    @MethodSource("lotesFueraDeTamano")
    void rechazaElTamanoDelLoteAntesDeMirarLosElementos(String caso, List<CreateTransactionCommand> lote) {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID, lote))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField)
                            .containsExactly("body");
                })
                .verify();

        verifyNoInteractions(cuentas, categorias, repositorio);
    }

    static Stream<Arguments> unCampoInvalido() {
        String ajena = TransactionMother.AJENA_ID.toString();
        String mercado = TransactionMother.MERCADO_ID.toString();
        String salario = TransactionMother.SALARIO_ID.toString();
        String origen = TransactionMother.ORIGEN_ID.toString();
        return Stream.of(
                Arguments.of("sin tipo", TransactionMother.unGasto().type(null), "type"),
                Arguments.of("tipo desconocido", TransactionMother.unGasto().type("REFUND"), "type"),
                Arguments.of("sin monto", TransactionMother.unGasto().amount(null), "amount"),
                Arguments.of("monto en cero", TransactionMother.unGasto().amount(BigDecimal.ZERO), "amount"),
                Arguments.of("monto negativo", TransactionMother.unGasto().amount(new BigDecimal("-1")), "amount"),
                Arguments.of("monto con cinco decimales",
                        TransactionMother.unGasto().amount(new BigDecimal("1.00001")), "amount"),
                Arguments.of("monto que desborda NUMERIC(18,4)",
                        TransactionMother.unGasto().amount(new BigDecimal("100000000000000")), "amount"),
                Arguments.of("sin cuenta", TransactionMother.unGasto().accountId(null), "accountId"),
                Arguments.of("cuenta que no es UUID", TransactionMother.unGasto().accountId("abc"), "accountId"),
                Arguments.of("cuenta ajena", TransactionMother.unGasto().accountId(ajena), "accountId"),
                Arguments.of("cuenta desactivada",
                        TransactionMother.unGasto().accountId(TransactionMother.INACTIVA_ID.toString()),
                        "accountId"),
                Arguments.of("cuenta en USD",
                        TransactionMother.unGasto().accountId(TransactionMother.USD_ID.toString()), "accountId"),
                Arguments.of("gasto sin categoria", TransactionMother.unGasto().categoryId(null), "categoryId"),
                Arguments.of("categoria que no es UUID", TransactionMother.unGasto().categoryId("x"), "categoryId"),
                Arguments.of("categoria ajena", TransactionMother.unGasto().categoryId(ajena), "categoryId"),
                Arguments.of("gasto con categoria de ingreso",
                        TransactionMother.unGasto().categoryId(salario), "categoryId"),
                Arguments.of("ingreso con categoria de gasto",
                        TransactionMother.unIngreso().categoryId(mercado), "categoryId"),
                Arguments.of("gasto con cuenta destino",
                        TransactionMother.unGasto().destinationAccountId(TransactionMother.DESTINO_ID.toString()),
                        "destinationAccountId"),
                Arguments.of("transferencia sin destino",
                        TransactionMother.unaTransferencia().destinationAccountId(null), "destinationAccountId"),
                Arguments.of("transferencia a la misma cuenta",
                        TransactionMother.unaTransferencia().destinationAccountId(origen), "destinationAccountId"),
                Arguments.of("transferencia a cuenta ajena",
                        TransactionMother.unaTransferencia().destinationAccountId(ajena), "destinationAccountId"),
                Arguments.of("transferencia a cuenta en USD",
                        TransactionMother.unaTransferencia()
                                .destinationAccountId(TransactionMother.USD_ID.toString()),
                        "destinationAccountId"),
                Arguments.of("transferencia con categoria",
                        TransactionMother.unaTransferencia().categoryId(mercado), "categoryId"),
                Arguments.of("monto de destino",
                        TransactionMother.unaTransferencia().destinationAmount(BigDecimal.TEN), "destinationAmount"),
                Arguments.of("moneda distinta de COP", TransactionMother.unGasto().currencyCode("USD"),
                        "currencyCode"),
                Arguments.of("sin descripcion", TransactionMother.unGasto().description(null), "description"),
                Arguments.of("descripcion en blanco", TransactionMother.unGasto().description("  "), "description"),
                Arguments.of("descripcion de 256",
                        TransactionMother.unGasto().description("x".repeat(256)), "description"),
                Arguments.of("notas de 1001", TransactionMother.unGasto().notes("x".repeat(1001)), "notes"),
                Arguments.of("sin fecha", TransactionMother.unGasto().occurredAt(null), "occurredAt"),
                Arguments.of("fecha sin offset",
                        TransactionMother.unGasto().occurredAt("2026-09-20T10:15:00"), "occurredAt"),
                Arguments.of("fecha que no es fecha", TransactionMother.unGasto().occurredAt("ayer"), "occurredAt"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoConSuIndiceSinGuardarNada(String caso, TransactionMother.Elemento elemento, String campo) {
        List<CreateTransactionCommand> lote = List.of(TransactionMother.unIngreso().build(), elemento.build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, lote))
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
    void aceptaNotasDeMilCaracteres() {
        StepVerifier.create(useCase().create(TransactionMother.USER_ID,
                        List.of(TransactionMother.unGasto().notes("x".repeat(1000)).build())))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void reportaTodosLosErroresDeTodosLosElementosJuntos() {
        List<CreateTransactionCommand> lote = List.of(
                TransactionMother.unGasto().build(),
                TransactionMother.unGasto().amount(new BigDecimal("-5")).build(),
                TransactionMother.unaTransferencia().categoryId(TransactionMother.MERCADO_ID.toString()).build(),
                TransactionMother.unIngreso().currencyCode("USD").build(),
                TransactionMother.unGasto().occurredAt("2026-09-21T10:00:00").description(" ").build());

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, lote))
                .expectErrorSatisfies(error -> assertThat(((BadRequestException) error)
                        .getErrorResponse().getErrors())
                        .extracting(ErrorDetail::getField)
                        .containsExactly("[1].amount", "[2].categoryId", "[3].currencyCode",
                                "[4].description", "[4].occurredAt"))
                .verify();

        verify(repositorio, never()).saveAll(any());
    }

    @Test
    void unElementoNuloEsUnErrorDeEseIndice() {
        List<CreateTransactionCommand> lote = new ArrayList<>();
        lote.add(TransactionMother.unGasto().build());
        lote.add(null);

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, lote))
                .expectErrorSatisfies(error -> assertThat(((BadRequestException) error)
                        .getErrorResponse().getErrors())
                        .extracting(ErrorDetail::getField)
                        .containsExactly("[1]"))
                .verify();
    }

    @Test
    void conElTipoInvalidoNoOpinaSobreCategoriaNiDestino() {
        CreateTransactionCommand raro = TransactionMother.unGasto().type("REFUND")
                .destinationAccountId(TransactionMother.DESTINO_ID.toString()).build();

        StepVerifier.create(useCase().create(TransactionMother.USER_ID, List.of(raro)))
                .expectErrorSatisfies(error -> assertThat(((BadRequestException) error)
                        .getErrorResponse().getErrors())
                        .extracting(ErrorDetail::getField)
                        .containsExactly("[0].type"))
                .verify();
    }

    @Test
    void propagaElFalloDelRepositorio() {
        when(repositorio.saveAll(anyList())).thenReturn(Flux.error(new IllegalStateException("se cayo la base")));

        StepVerifier.create(useCase().create(TransactionMother.USER_ID,
                        List.of(TransactionMother.unGasto().build())))
                .expectErrorMessage("se cayo la base")
                .verify();
    }

    @Test
    void noHaceNadaHastaQueAlguienSeSuscribe() {
        useCase().create(TransactionMother.USER_ID, List.of(TransactionMother.unGasto().build()));

        verifyNoInteractions(cuentas, categorias, repositorio);
    }

    private CreateTransactionsUseCase useCase() {
        return new CreateTransactionsUseCase(cuentas, categorias, repositorio, RELOJ);
    }
}
