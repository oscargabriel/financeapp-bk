package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.OriginalAmount;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UpdateTransactionCommand;
import com.oscargabriel.financeapp.domain.port.in.ApprovePendingTransactionPort;
import com.oscargabriel.financeapp.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.domain.port.in.DeleteTransactionPort;
import com.oscargabriel.financeapp.domain.port.in.ListPendingTransactionsPort;
import com.oscargabriel.financeapp.domain.port.in.RejectPendingTransactionPort;
import com.oscargabriel.financeapp.domain.port.in.UpdateTransactionPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.RelojFijo;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre TransactionsIT. */
@WebFluxTest(TransactionController.class)
@Import({SecurityConfig.class, JwtConfig.class, RelojFijo.class})
class TransactionControllerTest {

    private static final String URI_BASE = "/transactions";

    private static final UUID GASTO_ID = UUID.fromString("50000000-0000-7000-8000-000000000001");
    private static final UUID TRANSFERENCIA_ID = UUID.fromString("50000000-0000-7000-8000-000000000002");

    private static final String LOTE = """
            [{"type": "EXPENSE", "accountId": "30000000-0000-7000-8000-000000000001",
              "categoryId": "40000000-0000-7000-8000-000000000001", "amount": 50000,
              "description": "Mercado", "notes": "Pagado en efectivo", "occurredAt": "2026-09-20T10:15:00-05:00"},
             {"type": "TRANSFER", "accountId": "30000000-0000-7000-8000-000000000001",
              "destinationAccountId": "30000000-0000-7000-8000-000000000002", "amount": 100000,
              "currencyCode": "COP", "description": "Ahorro",
              "occurredAt": "2026-09-20T11:00:00-05:00"}]
            """;

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreateTransactionsPort createTransactions;

    @MockitoBean
    private UpdateTransactionPort updateTransaction;

    @MockitoBean
    private DeleteTransactionPort deleteTransaction;

    @MockitoBean
    private ListPendingTransactionsPort listPending;

    @MockitoBean
    private ApprovePendingTransactionPort approvePending;

    @MockitoBean
    private RejectPendingTransactionPort rejectPending;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(TransactionMother.USER_ID.toString()));
    }

    @Test
    void devuelve401CuandoNoHayCredenciales() {
        webTestClient.post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LOTE)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(createTransactions);
    }

    @Test
    void respondeCreadoConLosMovimientosEnElOrdenQueLosEntregaElCasoDeUso() {
        when(createTransactions.create(eq(TransactionMother.USER_ID), eq(TransactionOrigin.WEB), anyList()))
                .thenReturn(Flux.just(gasto(), transferencia()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LOTE)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo(GASTO_ID.toString())
                .jsonPath("$[0].type").isEqualTo("EXPENSE")
                .jsonPath("$[0].accountId").isEqualTo(TransactionMother.ORIGEN_ID.toString())
                .jsonPath("$[0].categoryId").isEqualTo(TransactionMother.MERCADO_ID.toString())
                .jsonPath("$[0].destinationAccountId").isEqualTo(null)
                .jsonPath("$[0].amount").isEqualTo(50000)
                .jsonPath("$[0].currencyCode").isEqualTo("COP")
                .jsonPath("$[0].description").isEqualTo("Mercado")
                .jsonPath("$[0].notes").isEqualTo("Pagado en efectivo")
                .jsonPath("$[0].occurredAt").isEqualTo("2026-09-20T15:15:00Z")
                .jsonPath("$[0].status").isEqualTo("CONFIRMED")
                .jsonPath("$[0].origin").isEqualTo("WEB")
                .jsonPath("$[0].userId").doesNotExist()
                .jsonPath("$[1].id").isEqualTo(TRANSFERENCIA_ID.toString())
                .jsonPath("$[1].destinationAccountId").isEqualTo(TransactionMother.DESTINO_ID.toString())
                .jsonPath("$[1].categoryId").isEqualTo(null);
    }

    /** FA-51: lo que entra al destino y lo recibido antes de convertir, en null cuando no aplican. */
    @Test
    void devuelveLosCamposDeMonedaDeCadaMovimiento() {
        Transaction convertido = TransactionMother.convertidoDesde(gasto(),
                new OriginalAmount(new BigDecimal("12.5"), "USD"));
        Transaction entreMonedas = TransactionMother.haciaOtraMoneda(transferencia(), TransactionMother.USD_ID,
                new BigDecimal("24.39"));
        when(createTransactions.create(eq(TransactionMother.USER_ID), eq(TransactionOrigin.WEB), anyList()))
                .thenReturn(Flux.just(convertido, entreMonedas, gasto()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LOTE)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("PENDING")
                .jsonPath("$[0].originalAmount").isEqualTo(12.5)
                .jsonPath("$[0].originalCurrencyCode").isEqualTo("USD")
                .jsonPath("$[0].destinationAmount").isEqualTo(null)
                .jsonPath("$[1].destinationAmount").isEqualTo(24.39)
                .jsonPath("$[1].originalAmount").isEqualTo(null)
                .jsonPath("$[2].destinationAmount").isEqualTo(null)
                .jsonPath("$[2].originalAmount").isEqualTo(null)
                .jsonPath("$[2].originalCurrencyCode").isEqualTo(null);
    }

    @Test
    void marcaComoProgramadoElMovimientoConFechaPosteriorAlReloj() {
        when(createTransactions.create(eq(TransactionMother.USER_ID), eq(TransactionOrigin.WEB), anyList()))
                .thenReturn(Flux.just(gasto(), TransactionMother.conFecha(transferencia(), RelojFijo.DESPUES)));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LOTE)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$[0].scheduled").isEqualTo(false)
                .jsonPath("$[1].scheduled").isEqualTo(true);
    }

    @Test
    void laMarcaDeProgramadoEnElCuerpoSeIgnora() {
        when(createTransactions.create(eq(TransactionMother.USER_ID), eq(TransactionOrigin.WEB), anyList()))
                .thenReturn(Flux.just(gasto()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        [{"type": "EXPENSE", "accountId": "30000000-0000-7000-8000-000000000001",
                          "categoryId": "40000000-0000-7000-8000-000000000001", "amount": 50000,
                          "description": "Mercado", "scheduled": true}]
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$[0].scheduled").isEqualTo(false);
    }

    @Test
    void pasaAlCasoDeUsoElUsuarioDelTokenYCadaElementoTalCualLlega() {
        when(createTransactions.create(eq(TransactionMother.USER_ID), eq(TransactionOrigin.WEB), anyList()))
                .thenReturn(Flux.just(gasto(), transferencia()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LOTE)
                .exchange()
                .expectStatus().isCreated();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateTransactionCommand>> lote = ArgumentCaptor.forClass(List.class);
        verify(createTransactions).create(eq(TransactionMother.USER_ID), eq(TransactionOrigin.WEB), lote.capture());
        assertThat(lote.getValue()).containsExactly(
                new CreateTransactionCommand("EXPENSE", "30000000-0000-7000-8000-000000000001", null,
                        "40000000-0000-7000-8000-000000000001", new BigDecimal("50000"), null, null,
                        "Mercado", "Pagado en efectivo", "2026-09-20T10:15:00-05:00"),
                new CreateTransactionCommand("TRANSFER", "30000000-0000-7000-8000-000000000001",
                        "30000000-0000-7000-8000-000000000002", null, new BigDecimal("100000"),
                        null, "COP", "Ahorro", null, "2026-09-20T11:00:00-05:00"));
    }

    /** La fecha la pone el caso de uso: el controlador no la exige ni la inventa (FA-60). */
    @Test
    void unElementoSinFechaLlegaAlCasoDeUsoSinFecha() {
        when(createTransactions.create(eq(TransactionMother.USER_ID), eq(TransactionOrigin.WEB), anyList())).thenReturn(Flux.just(gasto()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        [{"type": "EXPENSE", "accountId": "30000000-0000-7000-8000-000000000001",
                          "categoryId": "40000000-0000-7000-8000-000000000001", "amount": 50000,
                          "description": "Mercado"}]
                        """)
                .exchange()
                .expectStatus().isCreated();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateTransactionCommand>> lote = ArgumentCaptor.forClass(List.class);
        verify(createTransactions).create(eq(TransactionMother.USER_ID), eq(TransactionOrigin.WEB), lote.capture());
        assertThat(lote.getValue()).singleElement()
                .extracting(CreateTransactionCommand::occurredAt).isNull();
    }

    /** Las reglas de cada elemento viven en el record: el lote invalido no llega al caso de uso. */
    @Test
    void devuelve400ConLosErroresDeCadaElementoIndexadosSinLlamarAlCasoDeUso() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        [{"type": "EXPENSE", "accountId": "30000000-0000-7000-8000-000000000001",
                          "categoryId": "40000000-0000-7000-8000-000000000001", "amount": 50000,
                          "description": "Mercado", "occurredAt": "2026-09-20T10:15:00-05:00"},
                         {"type": "TRANSFER", "accountId": "30000000-0000-7000-8000-000000000001",
                          "amount": -5, "currencyCode": "USDX", "description": "Ahorro",
                          "occurredAt": "2026-09-20T11:00:00-05:00"}]
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(3)
                .jsonPath("$.errors[?(@.field == '[1].amount')].description")
                .isEqualTo("El monto debe ser mayor que cero: el signo lo da el tipo")
                .jsonPath("$.errors[?(@.field == '[1].currencyCode')].description")
                .isEqualTo("La moneda debe ser un codigo ISO 4217 de 3 letras")
                .jsonPath("$.errors[?(@.field == '[1].destinationAccountId')].description")
                .isEqualTo("La cuenta destino es obligatoria")
                .jsonPath("$.errors[?(@.code != 'VALIDATION_ERROR')]").isEmpty();

        verifyNoInteractions(createTransactions);
    }

    @Test
    void unElementoNuloEsUnErrorDeEseIndice() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        [{"type": "EXPENSE", "accountId": "30000000-0000-7000-8000-000000000001",
                          "categoryId": "40000000-0000-7000-8000-000000000001", "amount": 50000,
                          "description": "Mercado", "occurredAt": "2026-09-20T10:15:00-05:00"},
                         null]
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].description").isEqualTo("El elemento no puede ser nulo")
                .jsonPath("$.errors[0].field").isEqualTo("[1]");

        verifyNoInteractions(createTransactions);
    }

    @Test
    void rechazaUnLoteVacio() {
        rechazaElTamano("[]");
    }

    @Test
    void rechazaUnLoteDeMasDeQuinientos() {
        String elemento = """
                {"type": "EXPENSE", "accountId": "30000000-0000-7000-8000-000000000001",
                 "categoryId": "40000000-0000-7000-8000-000000000001", "amount": 1,
                 "description": "x", "occurredAt": "2026-09-20T10:15:00-05:00"}""";
        rechazaElTamano("[" + String.join(",", java.util.Collections.nCopies(501, elemento)) + "]");
    }

    /**
     * Con el tamano fuera del tope el lote se rechaza entero: reportar ademas los errores de 501
     * elementos devolveria miles de entradas por un unico problema.
     */
    @Test
    void elTamanoInvalidoTapaLosErroresDeLosElementos() {
        rechazaElTamano("[" + String.join(",", java.util.Collections.nCopies(501, "{}")) + "]");
    }

    private void rechazaElTamano(String lote) {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(lote)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].description").isEqualTo("El lote debe tener entre 1 y 500 movimientos")
                .jsonPath("$.errors[0].field").isEqualTo("body");

        verifyNoInteractions(createTransactions);
    }

    /** Que las cuentas y categorias existan y sean del usuario lo sigue reportando el caso de uso. */
    @Test
    void devuelveLosErroresIndexadosDelCasoDeUso() {
        when(createTransactions.create(eq(TransactionMother.USER_ID), eq(TransactionOrigin.WEB), anyList()))
                .thenReturn(Flux.error(new BadRequestException(HttpStatus.BAD_REQUEST,
                        ErrorCodes.VALIDATION_ERROR, "La cuenta no existe", "[1].accountId")));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LOTE)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("[1].accountId");
    }

    /**
     * Si el controlador transmitiera el Flux, el 201 y los primeros elementos saldrian antes de que
     * falle un INSERT posterior: la base hace rollback, pero el cliente creeria que esos entraron.
     */
    @Test
    void noRespondeCreadoSiElLoteFallaDespuesDelPrimerMovimiento() {
        when(createTransactions.create(eq(TransactionMother.USER_ID), eq(TransactionOrigin.WEB), anyList()))
                .thenReturn(Flux.concat(Flux.just(gasto()), Flux.error(new IllegalStateException("fallo el INSERT 2"))));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LOTE)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.INTERNAL_SERVER_ERROR.getCode())
                .jsonPath("$[0]").doesNotExist();
    }

    @Test
    void devuelve401CuandoElSubjectDelTokenNoEsUnUuid() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("no-es-uuid")))
                .post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LOTE)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.UNAUTHENTICATED.getCode());

        verifyNoInteractions(createTransactions);
    }

    @Test
    void modificaElMovimientoDelUsuarioYLoDevuelveCompleto() {
        when(updateTransaction.update(eq(TransactionMother.USER_ID), eq(GASTO_ID), any()))
                .thenReturn(Mono.just(gasto()));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + GASTO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"amount": 50000, "description": "Mercado", "notes": "se ignora"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(GASTO_ID.toString())
                .jsonPath("$.type").isEqualTo("EXPENSE")
                .jsonPath("$.amount").isEqualTo(50000)
                .jsonPath("$.occurredAt").isEqualTo("2026-09-20T15:15:00Z")
                .jsonPath("$.recurrenceId").isEqualTo(null)
                .jsonPath("$.installment").isEqualTo(null)
                .jsonPath("$.userId").doesNotExist();

        verify(updateTransaction).update(TransactionMother.USER_ID, GASTO_ID,
                new UpdateTransactionCommand(null, null, null, null, new BigDecimal("50000"), "Mercado", null, null));
    }

    @Test
    void elPatchQueLlevaLaFechaAlFuturoDevuelveElMovimientoProgramado() {
        when(updateTransaction.update(eq(TransactionMother.USER_ID), eq(GASTO_ID), any()))
                .thenReturn(Mono.just(TransactionMother.conFecha(gasto(), RelojFijo.DESPUES)));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + GASTO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"occurredAt": "2026-11-08T12:00:00-05:00"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.occurredAt").isEqualTo("2026-11-08T17:00:00Z")
                .jsonPath("$.scheduled").isEqualTo(true);
    }

    @Test
    void unaCuotaModificadaDiceDeQueCompraEsYSuNumeroSinElCapital() {
        UUID compra = UUID.fromString("90000000-0000-7000-8000-000000000001");
        when(updateTransaction.update(eq(TransactionMother.USER_ID), eq(GASTO_ID), any()))
                .thenReturn(Mono.just(TransactionMother.cuota(gasto(), compra)));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + GASTO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\": 400000, \"installment\": null}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.installment.purchaseId").isEqualTo(compra.toString())
                .jsonPath("$.installment.number").isEqualTo(2)
                .jsonPath("$.installment.count").isEqualTo(3)
                .jsonPath("$.installment.principal").doesNotExist();
    }

    @Test
    void unaOcurrenciaModificadaDevuelveSuSerie() {        UUID serie = UUID.fromString("80000000-0000-7000-8000-000000000001");
        when(updateTransaction.update(eq(TransactionMother.USER_ID), eq(GASTO_ID), any()))
                .thenReturn(Mono.just(TransactionMother.deLaSerie(gasto(), serie)));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + GASTO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"description\": \"Netflix\", \"recurrenceId\": null}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.recurrenceId").isEqualTo(serie.toString());
    }

    @Test
    void unParcheVacioEsUn400SobreElCuerpoSinLlamarAlCasoDeUso() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + GASTO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"description": null}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("body");

        verifyNoInteractions(updateTransaction);
    }

    @Test
    void unCampoDelParcheConFormatoInvalidoEsUn400SobreEseCampo() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + GASTO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"amount": 0}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].field").isEqualTo("amount");

        verifyNoInteractions(updateTransaction);
    }

    @Test
    void unIdMalFormadoEnElPatchEsUn400SobreElId() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/abc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"description": "Mercado"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verifyNoInteractions(updateTransaction);
    }

    @Test
    void elPatchPropagaElNoEncontradoDelCasoDeUso() {
        when(updateTransaction.update(eq(TransactionMother.USER_ID), eq(GASTO_ID), any()))
                .thenReturn(Mono.error(noEncontrado()));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + GASTO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"description": "Mercado"}
                        """)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.NOT_FOUND.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("id");
    }

    @Test
    void elPatchSinCredencialesEsUn401() {
        webTestClient.patch().uri(URI_BASE + "/" + GASTO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"description": "Mercado"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(updateTransaction);
    }

    @Test
    void eliminaElMovimientoYRespondeSinContenido() {
        when(deleteTransaction.delete(TransactionMother.USER_ID, GASTO_ID)).thenReturn(Mono.empty());

        webTestClient.mutateWith(tokenDelUsuario()).delete().uri(URI_BASE + "/" + GASTO_ID)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(deleteTransaction).delete(TransactionMother.USER_ID, GASTO_ID);
    }

    @Test
    void unIdMalFormadoEnElDeleteEsUn400SobreElId() {
        webTestClient.mutateWith(tokenDelUsuario()).delete().uri(URI_BASE + "/abc")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verifyNoInteractions(deleteTransaction);
    }

    @Test
    void elDeletePropagaElNoEncontradoDelCasoDeUso() {
        when(deleteTransaction.delete(TransactionMother.USER_ID, GASTO_ID)).thenReturn(Mono.error(noEncontrado()));

        webTestClient.mutateWith(tokenDelUsuario()).delete().uri(URI_BASE + "/" + GASTO_ID)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.NOT_FOUND.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("id");
    }

    @Test
    void elDeleteSinCredencialesEsUn401() {
        webTestClient.delete().uri(URI_BASE + "/" + GASTO_ID)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(deleteTransaction);
    }

    @Test
    void listaLosPendientesDelUsuarioConSuEstado() {
        when(listPending.listPending(TransactionMother.USER_ID)).thenReturn(Flux.just(
                TransactionMother.conEstado(gasto(), TransactionStatus.PENDING),
                TransactionMother.conEstado(transferencia(), TransactionStatus.PENDING)));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/pending")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo(GASTO_ID.toString())
                .jsonPath("$[0].status").isEqualTo("PENDING")
                .jsonPath("$[1].id").isEqualTo(TRANSFERENCIA_ID.toString())
                .jsonPath("$[1].status").isEqualTo("PENDING");
    }

    @Test
    void laListaDePendientesMarcaLosProgramados() {
        when(listPending.listPending(TransactionMother.USER_ID)).thenReturn(Flux.just(
                TransactionMother.conFecha(TransactionMother.conEstado(gasto(), TransactionStatus.PENDING),
                        RelojFijo.DESPUES),
                TransactionMother.conEstado(transferencia(), TransactionStatus.PENDING)));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/pending")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].scheduled").isEqualTo(true)
                .jsonPath("$[1].scheduled").isEqualTo(false);
    }

    @Test
    void sinPendientesLaListaSaleVacia() {
        when(listPending.listPending(TransactionMother.USER_ID)).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "/pending")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void apruebaYRespondeElMovimientoConfirmado() {
        when(approvePending.approve(TransactionMother.USER_ID, GASTO_ID)).thenReturn(Mono.just(gasto()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/" + GASTO_ID + "/approve")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(GASTO_ID.toString())
                .jsonPath("$.amount").isEqualTo(50000)
                .jsonPath("$.status").isEqualTo("CONFIRMED")
                .jsonPath("$.scheduled").isEqualTo(false);
    }

    @Test
    void rechazaYRespondeSinContenido() {
        when(rejectPending.reject(TransactionMother.USER_ID, GASTO_ID)).thenReturn(Mono.empty());

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/" + GASTO_ID + "/reject")
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(rejectPending).reject(TransactionMother.USER_ID, GASTO_ID);
    }

    @Test
    void unIdMalFormadoAlAprobarEsUn400SobreElId() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/abc/approve")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verifyNoInteractions(approvePending);
    }

    @Test
    void unIdMalFormadoAlRechazarEsUn400SobreElId() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/abc/reject")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verifyNoInteractions(rejectPending);
    }

    @Test
    void aprobarUnConfirmadoPropagaElConflictoDelCasoDeUso() {
        when(approvePending.approve(TransactionMother.USER_ID, GASTO_ID)).thenReturn(Mono.error(
                new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.INVALID_STATE,
                        "El movimiento ya esta confirmado", "status")));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/" + GASTO_ID + "/approve")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.INVALID_STATE.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("status");
    }

    @Test
    void rechazarUnoAjenoPropagaElNoEncontradoDelCasoDeUso() {
        when(rejectPending.reject(TransactionMother.USER_ID, GASTO_ID)).thenReturn(Mono.error(noEncontrado()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/" + GASTO_ID + "/reject")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("id");
    }

    private static BadRequestException noEncontrado() {
        return new BadRequestException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "El movimiento no existe", "id");
    }

    private static Transaction gasto() {
        return new Transaction(GASTO_ID, TransactionMother.USER_ID, TransactionType.EXPENSE,
                TransactionMother.ORIGEN_ID, null, TransactionMother.MERCADO_ID, new BigDecimal("50000"),
                "COP", "Mercado", "Pagado en efectivo", Instant.parse("2026-09-20T15:15:00Z"),
                TransactionStatus.CONFIRMED, TransactionOrigin.WEB, null, null, null, null);
    }

    private static Transaction transferencia() {
        return new Transaction(TRANSFERENCIA_ID, TransactionMother.USER_ID, TransactionType.TRANSFER,
                TransactionMother.ORIGEN_ID, TransactionMother.DESTINO_ID, null, new BigDecimal("100000"),
                "COP", "Ahorro", null, Instant.parse("2026-09-20T16:00:00Z"),
                TransactionStatus.CONFIRMED, TransactionOrigin.WEB, null, null, null, null);
    }
}
