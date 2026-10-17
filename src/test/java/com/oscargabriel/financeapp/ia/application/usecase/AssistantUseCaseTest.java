package com.oscargabriel.financeapp.ia.application.usecase;

import static com.oscargabriel.financeapp.ia.domain.model.AssistantChannel.WEB_CHAT;
import static com.oscargabriel.financeapp.ia.support.AsistenteMother.AHORROS_ID;
import static com.oscargabriel.financeapp.ia.support.AsistenteMother.EFECTIVO_ID;
import static com.oscargabriel.financeapp.ia.support.AsistenteMother.MERCADO_ID;
import static com.oscargabriel.financeapp.ia.support.AsistenteMother.SALARIO_ID;
import static com.oscargabriel.financeapp.ia.support.AsistenteMother.USER_ID;
import static com.oscargabriel.financeapp.ia.support.AsistenteMother.unMovimiento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.ia.domain.model.AssistantBalance;
import com.oscargabriel.financeapp.ia.domain.model.AssistantChannel;
import com.oscargabriel.financeapp.ia.domain.model.AssistantContext;
import com.oscargabriel.financeapp.ia.domain.model.AssistantContext.CategoriaConAmbito;
import com.oscargabriel.financeapp.ia.domain.model.AssistantDecision;
import com.oscargabriel.financeapp.ia.domain.model.AssistantFile;
import com.oscargabriel.financeapp.ia.domain.model.AssistantFileType;
import com.oscargabriel.financeapp.ia.domain.model.AssistantMessage;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReply;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReply.AssistantIntent;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReport;
import com.oscargabriel.financeapp.ia.domain.model.CategoryScope;
import com.oscargabriel.financeapp.ia.domain.model.CreatedGroup;
import com.oscargabriel.financeapp.ia.domain.model.CreatedMovement;
import com.oscargabriel.financeapp.ia.domain.model.NewMovement;
import com.oscargabriel.financeapp.ia.domain.model.RegisteredGroup;
import com.oscargabriel.financeapp.ia.domain.model.RegisteredMovement;
import com.oscargabriel.financeapp.ia.domain.model.ReportFilter;
import com.oscargabriel.financeapp.ia.domain.model.TransactionType;
import com.oscargabriel.financeapp.ia.domain.model.UserCategory;
import com.oscargabriel.financeapp.ia.domain.port.out.AssistantModelPort;
import com.oscargabriel.financeapp.ia.domain.port.out.BalanceQueryPort;
import com.oscargabriel.financeapp.ia.domain.port.out.NamesQueryPort;
import com.oscargabriel.financeapp.ia.domain.port.out.PendingMovementPort;
import com.oscargabriel.financeapp.ia.domain.port.out.ReportQueryPort;
import com.oscargabriel.financeapp.ia.domain.port.out.SeriesCatchUpPort;
import com.oscargabriel.financeapp.ia.support.AsistenteMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantUseCaseTest {

    private static final String TEXTO = "lo que escribio el usuario";
    private static final AssistantMessage MENSAJE = AssistantMessage.deTexto(TEXTO);
    private static final Instant AHORA = Instant.parse("2026-10-08T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneId.of("America/Bogota"));
    private static final UUID REGISTRADO_ID = UUID.fromString("30000000-0000-7000-8000-000000000001");
    private static final UUID GRUPO_ID = UUID.fromString("71000000-0000-7000-8000-000000000001");

    @Mock
    private AssistantModelPort modelo;

    @Mock
    private NamesQueryPort nombres;

    @Mock
    private PendingMovementPort pendientes;

    @Mock
    private SeriesCatchUpPort series;

    @Mock
    private ReportQueryPort reportes;

    @Mock
    private BalanceQueryPort saldos;

    @Captor
    private ArgumentCaptor<List<NewMovement>> nuevos;

    @Captor
    private ArgumentCaptor<AssistantContext> contexto;

    private final List<UserCategory> categoriasDelUsuario = new ArrayList<>(AsistenteMother.categorias());

    @BeforeEach
    void escenario() {
        when(nombres.findActiveAccounts(USER_ID)).thenReturn(Flux.fromIterable(AsistenteMother.cuentas()));
        when(nombres.findLiveCategories(USER_ID)).thenAnswer(invocacion -> Flux.fromIterable(categoriasDelUsuario));
        when(pendientes.register(eq(USER_ID), any(), any())).thenAnswer(invocacion -> Mono.just(registrados(
                invocacion.<List<NewMovement>>getArgument(2).size())));
        when(series.catchUp(USER_ID)).thenReturn(Mono.empty());
    }

    /** El primero con REGISTRADO_ID, los demas con ids que siguen; todos en GRUPO_ID. */
    private static RegisteredGroup registrados(int cuantos) {
        return new RegisteredGroup(GRUPO_ID, IntStream.range(0, cuantos)
                .mapToObj(i -> new RegisteredMovement(i == 0 ? REGISTRADO_ID : UUID.randomUUID(), "PENDING", AHORA))
                .toList());
    }

    private AssistantUseCase useCase() {
        return new AssistantUseCase(modelo, nombres, pendientes, series, reportes, saldos, RELOJ);
    }

    private void elModeloDecide(AssistantDecision decision) {
        when(modelo.interpretar(eq(MENSAJE), any())).thenReturn(Mono.just(decision));
    }

    /** Una llamada a crear_movimiento por movimiento, en ese orden. */
    private void elModeloDecide(AssistantDecision.CrearMovimiento... movimientos) {
        elModeloDecide(new AssistantDecision.CrearMovimientos(List.of(movimientos)));
    }

    private static AssistantDecision.CrearMovimiento gasto(String monto, String cuenta, String categoria,
            String descripcion, String fecha) {
        return new AssistantDecision.CrearMovimiento("EXPENSE", monto, cuenta, null, categoria, descripcion, fecha);
    }

    private void esAclaracion(AssistantReply respuesta, String... contiene) {
        assertThat(respuesta.intent()).isEqualTo(AssistantIntent.NEEDS_CLARIFICATION);
        assertThat(respuesta.message()).contains(contiene);
        assertThat(respuesta.group()).isNull();
        assertThat(respuesta.report()).isNull();
        assertThat(respuesta.balance()).isNull();
    }

    @Test
    void elModeloRecibeElTextoLaFechaDeHoyYLosNombresDeLasCuentasYCategoriasActivas() {
        elModeloDecide(new AssistantDecision.SinFuncion());

        StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).expectNextCount(1).verifyComplete();

        verify(modelo).interpretar(eq(MENSAJE), contexto.capture());
        assertThat(contexto.getValue().hoy()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(contexto.getValue().cuentas()).containsExactly("Efectivo", "Ahorros", "Ahorros USD");
        assertThat(contexto.getValue().categorias()).containsExactly(
                new CategoriaConAmbito("Mercado", CategoryScope.EXPENSE),
                new CategoriaConAmbito("Salario", CategoryScope.INCOME),
                new CategoriaConAmbito("Reintegros", CategoryScope.BOTH));
    }

    /** FIN-57: el archivo no cambia nada en el caso de uso; lo que el modelo saque de el se resuelve como un texto. */
    @Test
    void unMensajeConArchivoLlegaEnteroAlModeloYSuDecisionSeResuelveIgual() {
        AssistantMessage conRecibo = new AssistantMessage("con la efectivo",
                new AssistantFile(AssistantFileType.JPEG, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01}));
        when(modelo.interpretar(any(), any())).thenReturn(Mono.just(new AssistantDecision.CrearMovimientos(
                List.of(gasto("20000", "efectivo", "mercado", "Almuerzo", null)))));
        ArgumentCaptor<AssistantMessage> recibido = ArgumentCaptor.forClass(AssistantMessage.class);

        StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,conRecibo))
                .assertNext(respuesta -> assertThat(respuesta.intent()).isEqualTo(AssistantIntent.CREATE_TRANSACTION))
                .verifyComplete();

        verify(modelo).interpretar(recibido.capture(), any());
        assertThat(recibido.getValue()).isSameAs(conRecibo);
    }

    @Nested
    class CrearMovimiento {

        @Test
        void registraElGastoComoPendienteYLoDevuelveConLosNombres() {
            elModeloDecide(gasto("20000", "efectivo", "MERCADO", " Almuerzo ", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(respuesta -> {
                        assertThat(respuesta.intent()).isEqualTo(AssistantIntent.CREATE_TRANSACTION);
                        assertThat(respuesta.group()).isEqualTo(new CreatedGroup(GRUPO_ID, List.of(
                                new CreatedMovement(REGISTRADO_ID, TransactionType.EXPENSE, "PENDING",
                                        new BigDecimal("20000"), "COP", "Almuerzo", AHORA, "Efectivo", null,
                                        "Mercado"))));
                        assertThat(respuesta.message()).isEqualTo(
                                "Registre un gasto de 20.000 COP en Efectivo (Mercado). Confirmalo o descartalo.");
                        assertThat(respuesta.report()).isNull();
                        assertThat(respuesta.balance()).isNull();
                    })
                    .verifyComplete();

            verify(pendientes).register(eq(USER_ID), eq(WEB_CHAT), nuevos.capture());
            assertThat(nuevos.getValue().getFirst()).isEqualTo(new NewMovement(TransactionType.EXPENSE, EFECTIVO_ID, null,
                    MERCADO_ID, new BigDecimal("20000"), "COP", "Almuerzo", null));
        }

        /** FIN-112: el canal lo fija quien llama y llega tal cual al alta. */
        @ParameterizedTest
        @EnumSource(AssistantChannel.class)
        void registraConElCanalPorElQueEntroLaPeticion(AssistantChannel canal) {
            elModeloDecide(gasto("20000", "efectivo", "mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USER_ID, canal, MENSAJE)).expectNextCount(1).verifyComplete();

            verify(pendientes).register(eq(USER_ID), eq(canal), any());
        }

        @Test
        void registraUnIngresoConSuCategoriaDeIngreso() {
            elModeloDecide(new AssistantDecision.CrearMovimiento("income", "1500000", "Ahorros", null, "salario",
                    "Quincena", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).expectNextCount(1).verifyComplete();

            verify(pendientes).register(eq(USER_ID), eq(WEB_CHAT), nuevos.capture());
            assertThat(nuevos.getValue().getFirst()).extracting(NewMovement::type, NewMovement::accountId, NewMovement::categoryId)
                    .containsExactly(TransactionType.INCOME, AHORROS_ID, SALARIO_ID);
        }

        /** La categoria que mande el modelo en una transferencia se ignora: no la lleva. */
        @Test
        void registraUnaTransferenciaSinCategoria() {
            elModeloDecide(new AssistantDecision.CrearMovimiento("TRANSFER", "100000", "Efectivo", "ahorros",
                    "Mercado", "Ahorro", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> assertThat(r.group().transactions().getFirst())
                            .extracting(CreatedMovement::account, CreatedMovement::destinationAccount,
                                    CreatedMovement::category)
                            .containsExactly("Efectivo", "Ahorros", null))
                    .verifyComplete();

            verify(pendientes).register(eq(USER_ID), eq(WEB_CHAT), nuevos.capture());
            assertThat(nuevos.getValue().getFirst()).extracting(NewMovement::accountId, NewMovement::destinationAccountId,
                    NewMovement::categoryId).containsExactly(EFECTIVO_ID, AHORROS_ID, null);
        }

        @Test
        void unaCuentaQueNoExistePideAclaracionConLasCuentasDelUsuario() {
            elModeloDecide(gasto("20000", "Bancolombia", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "Bancolombia", "Efectivo", "Ahorros"))
                    .verifyComplete();
            verifyNoInteractions(pendientes);
        }

        /** Nequi existe pero esta desactivada: la consulta de nombres no la trae, como una ajena. */
        @Test
        void unaCuentaDesactivadaPideAclaracion() {
            elModeloDecide(gasto("20000", "Nequi", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "Nequi"))
                    .verifyComplete();
            verifyNoInteractions(pendientes);
        }

        @Test
        void unaCategoriaAmbiguaPideAclaracion() {
            categoriasDelUsuario.add(new UserCategory(UUID.randomUUID(), "mercado ", CategoryScope.EXPENSE));
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "Mercado"))
                    .verifyComplete();
            verifyNoInteractions(pendientes);
        }

        /** Salario es de ingreso: para un gasto no cuenta, y Mercado no sirve para un ingreso. */
        @Test
        void unaCategoriaQueNoAplicaAlTipoPideAclaracion() {
            elModeloDecide(gasto("20000", "Efectivo", "Salario", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "Salario", "Mercado"))
                    .verifyComplete();
            verifyNoInteractions(pendientes);
        }

        @Test
        void unGastoSinCategoriaPideAclaracion() {
            elModeloDecide(gasto("20000", "Efectivo", null, "Almuerzo", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "categoria"))
                    .verifyComplete();
            verifyNoInteractions(pendientes);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-5000", "veinte mil", "1.12345", "100000000000000"})
        void unMontoInvalidoPideAclaracion(String monto) {
            elModeloDecide(gasto(monto, "Efectivo", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "monto"))
                    .verifyComplete();
            verifyNoInteractions(pendientes);
        }

        @Test
        void unTipoDesconocidoPideAclaracion() {
            elModeloDecide(new AssistantDecision.CrearMovimiento("PRESTAMO", "20000", "Efectivo", null, "Mercado",
                    "Almuerzo", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "tipo"))
                    .verifyComplete();
            verifyNoInteractions(pendientes);
        }

        @Test
        void unaDescripcionVaciaOLargaPideAclaracion() {
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "x".repeat(256), null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "descripcion"))
                    .verifyComplete();
            verifyNoInteractions(pendientes);
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-10-08", " "})
        void sinFechaOConLaDeHoyUsaElInstanteDeLaPeticion(String fecha) {
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "Almuerzo", fecha));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).expectNextCount(1).verifyComplete();

            verify(pendientes).register(eq(USER_ID), eq(WEB_CHAT), nuevos.capture());
            assertThat(nuevos.getValue().getFirst().occurredAt()).isNull();
        }

        @Test
        void otroDiaVaALasDoceDeEseDiaEnLaZonaDeLaAplicacion() {
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "Almuerzo", "2026-10-05"));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).expectNextCount(1).verifyComplete();

            verify(pendientes).register(eq(USER_ID), eq(WEB_CHAT), nuevos.capture());
            assertThat(nuevos.getValue().getFirst().occurredAt()).isEqualTo("2026-10-05T12:00-05:00");
        }

        @Test
        void unaFechaQueNoEsUnDiaPideAclaracion() {
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "Almuerzo", "ayer"));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "fecha"))
                    .verifyComplete();
            verifyNoInteractions(pendientes);
        }

        /** Lo que rechaza el alta (aqui, una cuenta desactivada) se le explica al usuario con el mismo mensaje. */
        @Test
        void loQueRechazaElAltaPideAclaracionConSuMensaje() {
            when(pendientes.register(eq(USER_ID), any(), any()))
                    .thenReturn(Mono.error(new BadRequestException(HttpStatus.BAD_REQUEST, List.of(ErrorDetail.of(
                            ErrorCodes.VALIDATION_ERROR.getCode(), "La cuenta esta desactivada", "[0].accountId")))));
            elModeloDecide(gasto("20000", "Ahorros USD", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "La cuenta esta desactivada"))
                    .verifyComplete();
        }

        /** FIN-58: un mensaje con dos movimientos los registra juntos, en un grupo y en el orden del mensaje. */
        @Test
        void registraVariosMovimientosEnUnSoloGrupoYEnOrden() {
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "Almuerzo", null),
                    new AssistantDecision.CrearMovimiento("INCOME", "50000", "Ahorros", null, "Salario", "Bono", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> {
                        assertThat(r.intent()).isEqualTo(AssistantIntent.CREATE_TRANSACTION);
                        assertThat(r.group().id()).isEqualTo(GRUPO_ID);
                        assertThat(r.group().transactions()).extracting(CreatedMovement::description)
                                .containsExactly("Almuerzo", "Bono");
                        assertThat(r.message()).isEqualTo("Registre 2 movimientos: un gasto de 20.000 COP en Efectivo "
                                + "(Mercado); un ingreso de 50.000 COP en Ahorros (Salario). Confirmalos o descartalos.");
                    })
                    .verifyComplete();

            verify(pendientes).register(eq(USER_ID), eq(WEB_CHAT), nuevos.capture());
            assertThat(nuevos.getValue()).extracting(NewMovement::type, NewMovement::accountId)
                    .containsExactly(tuple(TransactionType.EXPENSE, EFECTIVO_ID), tuple(TransactionType.INCOME, AHORROS_ID));
        }

        @Test
        void siUnoNoSeResuelveNoRegistraNingunoYDiceCual() {
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "Almuerzo", null),
                    gasto("15000", "Bancolombia", "Mercado", "Taxi", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "Movimiento 2:", "Bancolombia"))
                    .verifyComplete();
            verifyNoInteractions(pendientes);
        }

        @Test
        void conUnSoloMovimientoLaAclaracionNoLoNumera() {
            elModeloDecide(gasto("20000", "Bancolombia", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> assertThat(r.message()).doesNotContain("Movimiento 1"))
                    .verifyComplete();
        }

        @Test
        void registrarNoPoneAlDiaLasSeries() {
            elModeloDecide(gasto("20000", "Efectivo", "Mercado", "Almuerzo", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).expectNextCount(1).verifyComplete();

            verifyNoInteractions(series);
        }
    }

    @Nested
    class ConsultarMovimientos {

        private final AssistantReport.Item gasto = unMovimiento(TransactionType.EXPENSE, "10", "40000");
        private final AssistantReport.Item ingreso = unMovimiento(TransactionType.INCOME, "100", "400000");
        private final AssistantReport.Item transferencia = unMovimiento(TransactionType.TRANSFER, "5", "20000");

        @BeforeEach
        void reporte() {
            when(reportes.findBaseCurrency(USER_ID)).thenReturn(Mono.just("COP"));
            when(reportes.findMovements(eq(USER_ID), any(), eq(AHORA)))
                    .thenReturn(Flux.just(ingreso, gasto, transferencia));
        }

        @Test
        void devuelveLosMovimientosYLosTotalesEnLaMonedaDelUsuario() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos("2026-10-01", "2026-10-08", null, null, null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> {
                        assertThat(r.intent()).isEqualTo(AssistantIntent.LIST_TRANSACTIONS);
                        assertThat(r.report()).isEqualTo(new AssistantReport(LocalDate.of(2026, 10, 1),
                                LocalDate.of(2026, 10, 8), "COP", new BigDecimal("400000"), new BigDecimal("40000"),
                                new BigDecimal("360000"), List.of(ingreso, gasto, transferencia)));
                        assertThat(r.message()).contains("3 movimientos", "neto");
                        assertThat(r.group()).isNull();
                        assertThat(r.balance()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        void filtraPorElRangoYElTipoPedidosALaHoraDeLaPeticion() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos("2026-10-01", "2026-10-08", "expense", null, null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).expectNextCount(1).verifyComplete();

            verify(reportes).findMovements(USER_ID, new ReportFilter(LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 10, 8), Set.of(), Set.of(), Set.of(TransactionType.EXPENSE)), AHORA);
        }

        @Test
        void sinRangoUsaElMesEnCurso() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos(null, null, null, null, null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).expectNextCount(1).verifyComplete();

            verify(reportes).findMovements(USER_ID, new ReportFilter(LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 10, 31), Set.of(), Set.of(), Set.of()), AHORA);
        }

        @Test
        void resuelveLaCategoriaYLaCuentaPorNombre() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos(null, null, null, "mercado", "efectivo"));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).expectNextCount(1).verifyComplete();

            verify(reportes).findMovements(USER_ID, new ReportFilter(LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 10, 31), Set.of(MERCADO_ID), Set.of(EFECTIVO_ID), Set.of()), AHORA);
        }

        @Test
        void poneAlDiaLasSeriesAntesDeLeer() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos(null, null, null, null, null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).expectNextCount(1).verifyComplete();

            InOrder orden = inOrder(series, reportes);
            orden.verify(series).catchUp(USER_ID);
            orden.verify(reportes).findMovements(eq(USER_ID), any(), eq(AHORA));
        }

        /** Ignorar el filtro daria totales que el usuario leeria como suyos. */
        @Test
        void unaCategoriaQueNoExistePideAclaracionSinTocarNada() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos(null, null, null, "Viajes", null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "Viajes"))
                    .verifyComplete();
            verifyNoInteractions(series, reportes);
        }

        @Test
        void soloUnExtremoDelRangoPideAclaracion() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos("2026-10-01", null, null, null, null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "fechas"))
                    .verifyComplete();
            verifyNoInteractions(series, reportes);
        }

        @Test
        void unRangoInvertidoPideAclaracionSinTocarNada() {
            elModeloDecide(new AssistantDecision.ConsultarMovimientos("2026-10-08", "2026-10-01", null, null, null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "2026-10-08", "posterior", "2026-10-01"))
                    .verifyComplete();
            verifyNoInteractions(series, reportes);
        }
    }

    @Nested
    class ConsultarSaldo {

        @BeforeEach
        void saldo() {
            when(saldos.findSums(eq(USER_ID), any(), any())).thenReturn(Mono.just(AsistenteMother.sumas()));
            when(saldos.findAccounts(USER_ID)).thenReturn(Flux.just(AsistenteMother.saldoDeEfectivo()));
        }

        @Test
        void sinRangoDevuelveElSaldoDelMes() {
            elModeloDecide(new AssistantDecision.ConsultarSaldo(null, null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> {
                        assertThat(r.intent()).isEqualTo(AssistantIntent.GET_BALANCE);
                        assertThat(r.balance()).isEqualTo(new AssistantBalance(LocalDate.of(2026, 10, 1),
                                LocalDate.of(2026, 10, 31), "COP", new BigDecimal("3000000"),
                                new BigDecimal("1200000"), new BigDecimal("1800000"), new BigDecimal("1500000"),
                                List.of(AsistenteMother.saldoDeEfectivo())));
                        assertThat(r.message()).contains("neto");
                    })
                    .verifyComplete();
            verify(saldos).findSums(USER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        }

        @Test
        void conRangoLoPasaTalCual() {
            elModeloDecide(new AssistantDecision.ConsultarSaldo("2026-09-01", "2026-09-30"));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).expectNextCount(1).verifyComplete();

            verify(saldos).findSums(USER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        }

        @Test
        void poneAlDiaLasSeriesAntesDeLeer() {
            elModeloDecide(new AssistantDecision.ConsultarSaldo(null, null));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).expectNextCount(1).verifyComplete();

            InOrder orden = inOrder(series, saldos);
            orden.verify(series).catchUp(USER_ID);
            orden.verify(saldos).findSums(eq(USER_ID), any(), any());
        }

        @Test
        void unaFechaQueNoEsUnDiaPideAclaracion() {
            elModeloDecide(new AssistantDecision.ConsultarSaldo("septiembre", "2026-09-30"));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "fecha"))
                    .verifyComplete();
            verifyNoInteractions(series, saldos);
        }

        @Test
        void unRangoInvertidoPideAclaracionSinTocarNada() {
            elModeloDecide(new AssistantDecision.ConsultarSaldo("2026-09-30", "2026-09-01"));

            StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                    .assertNext(r -> esAclaracion(r, "posterior"))
                    .verifyComplete();
            verifyNoInteractions(series, saldos);
        }
    }

    @Test
    void sinFuncionNoEsSoportadoYNoTocaNada() {
        elModeloDecide(new AssistantDecision.SinFuncion());

        StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE))
                .assertNext(r -> {
                    assertThat(r.intent()).isEqualTo(AssistantIntent.UNSUPPORTED);
                    assertThat(r.message()).contains("movimiento", "saldo");
                    assertThat(r.group()).isNull();
                    assertThat(r.report()).isNull();
                    assertThat(r.balance()).isNull();
                })
                .verifyComplete();
        verifyNoInteractions(pendientes, series, reportes, saldos);
    }

    @Test
    void unaFallaDelModeloSalePorElErrorDelPuerto() {
        BadRequestException caido = new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR,
                "No disponible", "server");
        when(modelo.interpretar(any(), any())).thenReturn(Mono.error(caido));

        StepVerifier.create(useCase().atender(USER_ID, WEB_CHAT,MENSAJE)).verifyErrorMatches(e -> e == caido);
        verifyNoInteractions(pendientes, series, reportes, saldos);
    }
}
