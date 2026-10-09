package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.oscargabriel.financeapp.domain.model.AssistantContext;
import com.oscargabriel.financeapp.domain.model.AssistantContext.CategoriaConAmbito;
import com.oscargabriel.financeapp.domain.model.AssistantDecision;
import com.oscargabriel.financeapp.domain.model.AssistantReply;
import com.oscargabriel.financeapp.domain.model.AssistantReply.AssistantIntent;
import com.oscargabriel.financeapp.domain.model.Balance;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.domain.model.TransactionReport;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.domain.port.in.GetBalancePort;
import com.oscargabriel.financeapp.domain.port.in.GetTransactionReportPort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.AssistantModelPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.support.BalanceMother;
import com.oscargabriel.financeapp.support.ReportMother;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantUseCaseTest {

    private static final java.util.UUID USUARIO = TransactionMother.USER_ID;
    private static final String TEXTO = "lo que escribio el usuario";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private AssistantModelPort modelo;

    @Mock
    private AccountQueryPort cuentas;

    @Mock
    private CategoryQueryPort categorias;

    @Mock
    private CreateTransactionsPort createTransactions;

    @Mock
    private GetTransactionReportPort getTransactionReport;

    @Mock
    private GetBalancePort getBalance;

    @Captor
    private ArgumentCaptor<List<CreateTransactionCommand>> lote;

    @Captor
    private ArgumentCaptor<AssistantContext> contexto;

    private final List<Category> categoriasDelUsuario = new ArrayList<>(TransactionMother.categoriasDelUsuario());

    @BeforeEach
    void escenario() {
        when(cuentas.findByUser(USUARIO, false)).thenReturn(Flux.fromIterable(
                TransactionMother.cuentasDelUsuario().stream().filter(c -> c.active()).toList()));
        when(categorias.findActiveByUser(eq(USUARIO), any()))
                .thenAnswer(invocacion -> Flux.fromIterable(categoriasDelUsuario));
        when(createTransactions.create(eq(USUARIO), eq(TransactionOrigin.TELEGRAM), anyList()))
                .thenReturn(Flux.just(TransactionMother.unGastoPendiente()));
    }

    private AssistantUseCase useCase() {
        return new AssistantUseCase(modelo, cuentas, categorias, createTransactions, getTransactionReport,
                getBalance, RELOJ);
    }

    private void elModeloDecide(AssistantDecision decision) {
        when(modelo.interpretar(eq(TEXTO), any())).thenReturn(Mono.just(decision));
    }

    private static AssistantDecision.CrearMovimiento gasto(String monto, String cuenta, String categoria,
            String descripcion, String fecha) {
        return new AssistantDecision.CrearMovimiento("EXPENSE", monto, cuenta, null, categoria, descripcion, fecha);
    }

    private void esAclaracion(AssistantReply respuesta, String... contiene) {
        assertThat(respuesta.intent()).isEqualTo(AssistantIntent.NEEDS_CLARIFICATION);
        assertThat(respuesta.message()).contains(contiene);
        assertThat(respuesta.transaction()).isNull();
        assertThat(respuesta.report()).isNull();
        assertThat(respuesta.balance()).isNull();
    }

    @Test
    void elModeloRecibeElTextoLaFechaDeHoyYLosNombresDeLasCuentasYCategoriasActivas() {
        elModeloDecide(new AssistantDecision.SinFuncion());

        StepVerifier.create(useCase().atender(USUARIO, TEXTO)).expectNextCount(1).verifyComplete();

        verify(modelo).interpretar(eq(TEXTO), contexto.capture());
        assertThat(contexto.getValue().hoy()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(contexto.getValue().cuentas()).containsExactly("Efectivo", "Ahorros", "Ahorros USD");
        assertThat(contexto.getValue().categorias()).containsExactly(
                new CategoriaConAmbito("Mercado", CategoryScope.EXPENSE),
                new CategoriaConAmbito("Salario", CategoryScope.INCOME),
                new CategoriaConAmbito("Reintegros", CategoryScope.BOTH));
    }

    @Nested
    class CrearMovimiento {

        @Test
        void registraElGastoComoPendienteDelAsistente() {
            elModeloDecide(gasto("20000", "efectivo", "MERCADO", " Almuerzo ", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(respuesta -> {
                        assertThat(respuesta.intent()).isEqualTo(AssistantIntent.CREATE_TRANSACTION);
                        assertThat(respuesta.transaction()).isEqualTo(TransactionMother.unGastoPendiente());
                        assertThat(respuesta.message()).contains("pendiente");
                        assertThat(respuesta.report()).isNull();
                        assertThat(respuesta.balance()).isNull();
                    })
                    .verifyComplete();

            verify(createTransactions).create(eq(USUARIO), eq(TransactionOrigin.TELEGRAM), lote.capture());
            assertThat(lote.getValue()).singleElement().isEqualTo(new CreateTransactionCommand("EXPENSE",
                    TransactionMother.ORIGEN_ID.toString(), null, TransactionMother.MERCADO_ID.toString(),
                    new BigDecimal("20000"), null, "COP", "Almuerzo", null, null));
        }

        @Test
        void registraUnIngresoConSuCategoriaDeIngreso() {
            elModeloDecide(new AssistantDecision.CrearMovimiento("income", "1500000", "Ahorros", null, "salario",
                    "Quincena", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO)).expectNextCount(1).verifyComplete();

            verify(createTransactions).create(eq(USUARIO), eq(TransactionOrigin.TELEGRAM), lote.capture());
            assertThat(lote.getValue().getFirst()).extracting(CreateTransactionCommand::type,
                            CreateTransactionCommand::accountId, CreateTransactionCommand::categoryId)
                    .containsExactly("INCOME", TransactionMother.DESTINO_ID.toString(),
                            TransactionMother.SALARIO_ID.toString());
        }

        /** La categoria que mande el modelo en una transferencia se ignora: no la lleva. */
        @Test
        void registraUnaTransferenciaSinCategoria() {
            elModeloDecide(new AssistantDecision.CrearMovimiento("TRANSFER", "100000", "Efectivo", "ahorros",
                    "Mercado", "Ahorro", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO)).expectNextCount(1).verifyComplete();

            verify(createTransactions).create(eq(USUARIO), eq(TransactionOrigin.TELEGRAM), lote.capture());
            assertThat(lote.getValue().getFirst()).extracting(CreateTransactionCommand::accountId,
                            CreateTransactionCommand::destinationAccountId, CreateTransactionCommand::categoryId)
                    .containsExactly(TransactionMother.ORIGEN_ID.toString(), TransactionMother.DESTINO_ID.toString(),
                            null);
        }

        @Test
        void unaCuentaQueNoExistePideAclaracionConLasCuentasDelUsuario() {
            elModeloDecide(gasto("20000", "Bancolombia", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "Bancolombia", "Efectivo", "Ahorros"))
                    .verifyComplete();
            verifyNoInteractions(createTransactions);
        }

        /** Nequi existe pero esta desactivada: para el asistente no esta, como una ajena. */
        @Test
        void unaCuentaDesactivadaPideAclaracion() {
            elModeloDecide(gasto("20000", "Nequi", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "Nequi"))
                    .verifyComplete();
            verifyNoInteractions(createTransactions);
        }

        @Test
        void unaCategoriaAmbiguaPideAclaracion() {
            categoriasDelUsuario.add(new Category(java.util.UUID.randomUUID(), "mercado ", CategoryScope.EXPENSE,
                    null, null, false));
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "Mercado"))
                    .verifyComplete();
            verifyNoInteractions(createTransactions);
        }

        /** Salario es de ingreso: para un gasto no cuenta, y Mercado no sirve para un ingreso. */
        @Test
        void unaCategoriaQueNoAplicaAlTipoPideAclaracion() {
            elModeloDecide(gasto("20000", "Efectivo", "Salario", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "Salario", "Mercado"))
                    .verifyComplete();
            verifyNoInteractions(createTransactions);
        }

        @Test
        void unGastoSinCategoriaPideAclaracion() {
            elModeloDecide(gasto("20000", "Efectivo", null, "Almuerzo", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "categoria"))
                    .verifyComplete();
            verifyNoInteractions(createTransactions);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-5000", "veinte mil", "1.12345", "100000000000000"})
        void unMontoInvalidoPideAclaracion(String monto) {
            elModeloDecide(gasto(monto, "Efectivo", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "monto"))
                    .verifyComplete();
            verifyNoInteractions(createTransactions);
        }

        @Test
        void unTipoDesconocidoPideAclaracion() {
            elModeloDecide(new AssistantDecision.CrearMovimiento("PRESTAMO", "20000", "Efectivo", null, "Mercado",
                    "Almuerzo", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "tipo"))
                    .verifyComplete();
            verifyNoInteractions(createTransactions);
        }

        @Test
        void unaDescripcionVaciaOLargaPideAclaracion() {
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "x".repeat(256), null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "descripcion"))
                    .verifyComplete();
            verifyNoInteractions(createTransactions);
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-10-08", " "})
        void sinFechaOConLaDeHoyUsaElInstanteDeLaPeticion(String fecha) {
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "Almuerzo", fecha));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO)).expectNextCount(1).verifyComplete();

            verify(createTransactions).create(eq(USUARIO), eq(TransactionOrigin.TELEGRAM), lote.capture());
            assertThat(lote.getValue().getFirst().occurredAt()).isNull();
        }

        @Test
        void otroDiaVaALasDoceDeEseDiaEnLaZonaDeLaAplicacion() {
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "Almuerzo", "2026-10-05"));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO)).expectNextCount(1).verifyComplete();

            verify(createTransactions).create(eq(USUARIO), eq(TransactionOrigin.TELEGRAM), lote.capture());
            assertThat(lote.getValue().getFirst().occurredAt()).isEqualTo("2026-10-05T12:00-05:00");
        }

        @Test
        void unaFechaQueNoEsUnDiaPideAclaracion() {
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "Almuerzo", "ayer"));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "fecha"))
                    .verifyComplete();
            verifyNoInteractions(createTransactions);
        }

        /** Lo que rechaza el alta (aqui, una cuenta en dolares) se le explica al usuario con el mismo mensaje. */
        @Test
        void loQueRechazaElAltaPideAclaracionConSuMensaje() {
            when(createTransactions.create(eq(USUARIO), eq(TransactionOrigin.TELEGRAM), anyList()))
                    .thenReturn(Flux.error(new BadRequestException(HttpStatus.BAD_REQUEST, List.of(ErrorDetail.of(
                            ErrorCodes.VALIDATION_ERROR.getCode(), "La cuenta no es en COP", "[0].accountId")))));
            elModeloDecide(gasto("20000", "Ahorros USD", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "La cuenta no es en COP"))
                    .verifyComplete();
        }
    }

    @Nested
    class ConsultarMovimientos {

        private final TransactionReport reporte = TransactionReport.of("COP", ReportMother.sinFiltros(), List.of());

        @BeforeEach
        void reporte() {
            when(getTransactionReport.get(eq(USUARIO), any(), any(), any(), any(), any())).thenReturn(Mono.just(reporte));
        }

        @Test
        void devuelveElReporteDelRangoYTipoPedidos() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos("2026-10-01", "2026-10-08", "expense", null, null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> {
                        assertThat(r.intent()).isEqualTo(AssistantIntent.LIST_TRANSACTIONS);
                        assertThat(r.report()).isSameAs(reporte);
                        assertThat(r.transaction()).isNull();
                        assertThat(r.balance()).isNull();
                    })
                    .verifyComplete();
            verify(getTransactionReport).get(USUARIO, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), Set.of(),
                    Set.of(), Set.of(TransactionType.EXPENSE));
        }

        @Test
        void sinRangoUsaElMesEnCurso() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos(null, null, null, null, null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO)).expectNextCount(1).verifyComplete();

            verify(getTransactionReport).get(USUARIO, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), Set.of(),
                    Set.of(), Set.of());
        }

        @Test
        void resuelveLaCategoriaYLaCuentaPorNombre() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos(null, null, null, "mercado", "efectivo"));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO)).expectNextCount(1).verifyComplete();

            verify(getTransactionReport).get(USUARIO, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                    Set.of(TransactionMother.MERCADO_ID), Set.of(TransactionMother.ORIGEN_ID), Set.of());
        }

        /** Ignorar el filtro daria totales que el usuario leeria como suyos. */
        @Test
        void unaCategoriaQueNoExistePideAclaracion() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos(null, null, null, "Viajes", null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "Viajes"))
                    .verifyComplete();
            verifyNoInteractions(getTransactionReport);
        }

        @Test
        void soloUnExtremoDelRangoPideAclaracion() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos("2026-10-01", null, null, null, null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "fechas"))
                    .verifyComplete();
            verifyNoInteractions(getTransactionReport);
        }

        @Test
        void loQueRechazaElReportePideAclaracionConSuMensaje() {
            when(getTransactionReport.get(eq(USUARIO), any(), any(), any(), any(), any()))
                    .thenReturn(Mono.error(new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                            "El rango no puede superar un ano", "to")));
            elModeloDecide(new AssistantDecision.ConsultarMovimientos("2020-01-01", "2026-10-08", null, null, null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "El rango no puede superar un ano"))
                    .verifyComplete();
        }
    }

    @Nested
    class ConsultarSaldo {

        private final Balance saldo = Balance.of(BalanceMother.DESDE, BalanceMother.HASTA,
                BalanceMother.sumasDelEscenario(), List.of());

        @BeforeEach
        void saldo() {
            when(getBalance.get(eq(USUARIO), any(), any())).thenReturn(Mono.just(saldo));
        }

        @Test
        void sinRangoPideElSaldoDelMes() {
            elModeloDecide(new AssistantDecision.ConsultarSaldo(null, null));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> {
                        assertThat(r.intent()).isEqualTo(AssistantIntent.GET_BALANCE);
                        assertThat(r.balance()).isSameAs(saldo);
                        assertThat(r.message()).contains("neto");
                    })
                    .verifyComplete();
            verify(getBalance).get(USUARIO, null, null);
        }

        @Test
        void conRangoLoPasaTalCual() {
            elModeloDecide(new AssistantDecision.ConsultarSaldo("2026-09-01", "2026-09-30"));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO)).expectNextCount(1).verifyComplete();

            verify(getBalance).get(USUARIO, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        }

        @Test
        void unaFechaQueNoEsUnDiaPideAclaracion() {
            elModeloDecide(new AssistantDecision.ConsultarSaldo("septiembre", "2026-09-30"));

            StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                    .assertNext(r -> esAclaracion(r, "fecha"))
                    .verifyComplete();
            verifyNoInteractions(getBalance);
        }
    }

    @Test
    void sinFuncionNoEsSoportadoYNoTocaNada() {
        elModeloDecide(new AssistantDecision.SinFuncion());

        StepVerifier.create(useCase().atender(USUARIO, TEXTO))
                .assertNext(r -> {
                    assertThat(r.intent()).isEqualTo(AssistantIntent.UNSUPPORTED);
                    assertThat(r.message()).contains("movimiento", "saldo");
                    assertThat(r.transaction()).isNull();
                    assertThat(r.report()).isNull();
                    assertThat(r.balance()).isNull();
                })
                .verifyComplete();
        verifyNoInteractions(createTransactions, getTransactionReport, getBalance);
    }

    @Test
    void unaFallaDelModeloSalePorElErrorDelPuerto() {
        BadRequestException caido = new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR,
                "No disponible", "server");
        when(modelo.interpretar(anyString(), any())).thenReturn(Mono.error(caido));

        StepVerifier.create(useCase().atender(USUARIO, TEXTO)).verifyErrorMatches(e -> e == caido);
        verifyNoInteractions(createTransactions, getTransactionReport, getBalance);
    }
}
