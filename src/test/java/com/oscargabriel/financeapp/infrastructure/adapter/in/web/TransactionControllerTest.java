package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre TransactionsIT. */
@WebFluxTest(TransactionController.class)
@Import({SecurityConfig.class, JwtConfig.class})
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
              "destinationAmount": 1, "currencyCode": "COP", "description": "Ahorro",
              "occurredAt": "2026-09-20T11:00:00-05:00"}]
            """;

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreateTransactionsPort createTransactions;

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
        when(createTransactions.create(eq(TransactionMother.USER_ID), anyList()))
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
                .jsonPath("$[0].userId").doesNotExist()
                .jsonPath("$[1].id").isEqualTo(TRANSFERENCIA_ID.toString())
                .jsonPath("$[1].destinationAccountId").isEqualTo(TransactionMother.DESTINO_ID.toString())
                .jsonPath("$[1].categoryId").isEqualTo(null);
    }

    @Test
    void pasaAlCasoDeUsoElUsuarioDelTokenYCadaElementoTalCualLlega() {
        when(createTransactions.create(eq(TransactionMother.USER_ID), anyList()))
                .thenReturn(Flux.just(gasto(), transferencia()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LOTE)
                .exchange()
                .expectStatus().isCreated();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateTransactionCommand>> lote = ArgumentCaptor.forClass(List.class);
        verify(createTransactions).create(eq(TransactionMother.USER_ID), lote.capture());
        assertThat(lote.getValue()).containsExactly(
                new CreateTransactionCommand("EXPENSE", "30000000-0000-7000-8000-000000000001", null,
                        "40000000-0000-7000-8000-000000000001", new BigDecimal("50000"), null, null,
                        "Mercado", "Pagado en efectivo", "2026-09-20T10:15:00-05:00"),
                new CreateTransactionCommand("TRANSFER", "30000000-0000-7000-8000-000000000001",
                        "30000000-0000-7000-8000-000000000002", null, new BigDecimal("100000"),
                        BigDecimal.ONE, "COP", "Ahorro", null, "2026-09-20T11:00:00-05:00"));
    }

    @Test
    void devuelveLosErroresIndexadosDelCasoDeUso() {
        when(createTransactions.create(eq(TransactionMother.USER_ID), anyList()))
                .thenReturn(Flux.error(new BadRequestException(HttpStatus.BAD_REQUEST,
                        ErrorCodes.VALIDATION_ERROR, "El monto debe ser mayor que cero", "[1].amount")));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LOTE)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("[1].amount");
    }

    /**
     * Si el controlador transmitiera el Flux, el 201 y los primeros elementos saldrian antes de que
     * falle un INSERT posterior: la base hace rollback, pero el cliente creeria que esos entraron.
     */
    @Test
    void noRespondeCreadoSiElLoteFallaDespuesDelPrimerMovimiento() {
        when(createTransactions.create(eq(TransactionMother.USER_ID), anyList()))
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

    private static Transaction gasto() {
        return new Transaction(GASTO_ID, TransactionMother.USER_ID, TransactionType.EXPENSE,
                TransactionMother.ORIGEN_ID, null, TransactionMother.MERCADO_ID, new BigDecimal("50000"),
                "COP", "Mercado", "Pagado en efectivo", Instant.parse("2026-09-20T15:15:00Z"));
    }

    private static Transaction transferencia() {
        return new Transaction(TRANSFERENCIA_ID, TransactionMother.USER_ID, TransactionType.TRANSFER,
                TransactionMother.ORIGEN_ID, TransactionMother.DESTINO_ID, null, new BigDecimal("100000"),
                "COP", "Ahorro", null, Instant.parse("2026-09-20T16:00:00Z"));
    }
}
