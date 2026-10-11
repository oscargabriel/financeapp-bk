package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static com.oscargabriel.financeapp.support.InstallmentMother.COMPRA_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.domain.model.CreatedInstallmentPurchase;
import com.oscargabriel.financeapp.domain.model.GroupScope;
import com.oscargabriel.financeapp.domain.model.InstallmentPreview;
import com.oscargabriel.financeapp.domain.model.InstallmentPurchaseView;
import com.oscargabriel.financeapp.domain.model.ScheduledInstallment;
import com.oscargabriel.financeapp.domain.model.UpdateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.domain.port.in.CancelInstallmentPurchasePort;
import com.oscargabriel.financeapp.domain.port.in.CreateInstallmentPurchasePort;
import com.oscargabriel.financeapp.domain.port.in.ListInstallmentPurchasesPort;
import com.oscargabriel.financeapp.domain.port.in.PreviewInstallmentPurchasePort;
import com.oscargabriel.financeapp.domain.port.in.UpdateInstallmentPurchasePort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.InstallmentMother;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre InstallmentPurchasesIT. */
@WebFluxTest(InstallmentPurchaseController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class InstallmentPurchaseControllerTest {

    private static final String URI_BASE = "/installment-purchases";

    private static final String ALTA = """
            {"accountId": "30000000-0000-7000-8000-000000000012",
             "categoryId": "40000000-0000-7000-8000-000000000001", "amount": 1200000,
             "description": "Televisor", "purchaseDate": "2026-10-09", "installmentCount": 3}
            """;

    private static final UUID CUOTA_1 = UUID.fromString("50000000-0000-7000-8000-000000000011");

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PreviewInstallmentPurchasePort previewPurchase;

    @MockitoBean
    private CreateInstallmentPurchasePort createPurchase;

    @MockitoBean
    private ListInstallmentPurchasesPort listPurchases;

    @MockitoBean
    private UpdateInstallmentPurchasePort updatePurchase;

    @MockitoBean
    private CancelInstallmentPurchasePort cancelPurchase;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(TransactionMother.USER_ID.toString()));
    }

    /** Simulado sin ids de movimiento; recien creado, cada cuota con el suyo. */
    private static List<ScheduledInstallment> plan(UUID primera) {
        return List.of(
                new ScheduledInstallment(1, primera, Instant.parse("2026-11-05T05:00:00Z"), new BigDecimal("400000"),
                        new BigDecimal("24000")),
                new ScheduledInstallment(2, primera == null ? null : UUID.randomUUID(), Instant.parse("2026-12-05T05:00:00Z"), new BigDecimal("400000"),
                        new BigDecimal("16000")),
                new ScheduledInstallment(3, primera == null ? null : UUID.randomUUID(), Instant.parse("2027-01-05T05:00:00Z"), new BigDecimal("400000"),
                        new BigDecimal("8000")));
    }

    @Test
    void laSimulacionDevuelveElPlan() {
        when(previewPurchase.preview(eq(TransactionMother.USER_ID), any())).thenReturn(Mono.just(new InstallmentPreview(
                InstallmentMother.VISA_ID, new BigDecimal("1200000"), "COP", LocalDate.of(2026, 10, 9), 3,
                new BigDecimal("2.0000"), plan(null))));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ALTA)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accountId").isEqualTo(InstallmentMother.VISA_ID.toString())
                .jsonPath("$.amount").isEqualTo(1200000)
                .jsonPath("$.currencyCode").isEqualTo("COP")
                .jsonPath("$.purchaseDate").isEqualTo("2026-10-09")
                .jsonPath("$.installmentCount").isEqualTo(3)
                .jsonPath("$.monthlyInterestRate").isEqualTo(2.0)
                .jsonPath("$.totalInterest").isEqualTo(48000)
                .jsonPath("$.totalAmount").isEqualTo(1248000)
                .jsonPath("$.installments.length()").isEqualTo(3)
                .jsonPath("$.installments[0].number").isEqualTo(1)
                .jsonPath("$.installments[0].dueAt").isEqualTo("2026-11-05T05:00:00Z")
                .jsonPath("$.installments[0].principal").isEqualTo(400000)
                .jsonPath("$.installments[0].interest").isEqualTo(24000)
                .jsonPath("$.installments[0].amount").isEqualTo(424000)
                .jsonPath("$.installments[0].transactionId").doesNotExist();

        ArgumentCaptor<CreateInstallmentPurchaseCommand> compra =
                ArgumentCaptor.forClass(CreateInstallmentPurchaseCommand.class);
        verify(previewPurchase).preview(eq(TransactionMother.USER_ID), compra.capture());
        assertThat(compra.getValue().purchaseDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(compra.getValue().installmentCount()).isEqualTo(3);
    }

    @Test
    void unaSimulacionInvalidaNoLlegaAlCasoDeUso() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE + "/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ALTA.replace("\"installmentCount\": 3", "\"installmentCount\": 49"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("installmentCount");

        verifyNoInteractions(previewPurchase);
    }

    @Test
    void elAltaRespondeCreadaConLaCompraYSuPlan() {
        when(createPurchase.create(eq(TransactionMother.USER_ID), any())).thenReturn(Mono.just(
                new CreatedInstallmentPurchase(InstallmentMother.televisorSinPagar(), plan(CUOTA_1))));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ALTA)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(COMPRA_ID.toString())
                .jsonPath("$.accountId").isEqualTo(InstallmentMother.VISA_ID.toString())
                .jsonPath("$.categoryId").isEqualTo(TransactionMother.MERCADO_ID.toString())
                .jsonPath("$.amount").isEqualTo(1200000)
                .jsonPath("$.currencyCode").isEqualTo("COP")
                .jsonPath("$.description").isEqualTo("Televisor")
                .jsonPath("$.purchaseDate").isEqualTo("2026-10-09")
                .jsonPath("$.installmentCount").isEqualTo(3)
                .jsonPath("$.monthlyInterestRate").isEqualTo(2.0)
                .jsonPath("$.paidCount").isEqualTo(0)
                .jsonPath("$.remainingPrincipal").isEqualTo(1200000)
                .jsonPath("$.remainingAmount").isEqualTo(1248000)
                .jsonPath("$.nextInstallment.number").isEqualTo(1)
                .jsonPath("$.nextInstallment.transactionId").isEqualTo(CUOTA_1.toString())
                .jsonPath("$.nextInstallment.dueAt").isEqualTo("2026-11-05T05:00:00Z")
                .jsonPath("$.nextInstallment.amount").isEqualTo(424000)
                .jsonPath("$.installments.length()").isEqualTo(3)
                .jsonPath("$.installments[0].transactionId").isEqualTo(CUOTA_1.toString())
                .jsonPath("$.installments[0].interest").isEqualTo(24000)
                .jsonPath("$.userId").doesNotExist();
    }

    @Test
    void elListadoDevuelveElResumenSinElPlan() {
        when(listPurchases.list(TransactionMother.USER_ID)).thenReturn(Flux.just(InstallmentMother.televisorSinPagar()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo(COMPRA_ID.toString())
                .jsonPath("$[0].nextInstallment.number").isEqualTo(1)
                .jsonPath("$[0].installments").doesNotExist();
    }

    @Test
    void unaCompraSinCuotasPorVenirTraeLaProximaEnNull() {
        InstallmentPurchaseView pagada = new InstallmentPurchaseView(
                InstallmentMother.televisor(), 3, BigDecimal.ZERO, BigDecimal.ZERO, null);
        when(updatePurchase.update(eq(TransactionMother.USER_ID), eq(COMPRA_ID), any())).thenReturn(Mono.just(pagada));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + COMPRA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"scope\": \"ALL\", \"description\": \"TV\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.nextInstallment").isEqualTo(null)
                .jsonPath("$.paidCount").isEqualTo(3);
    }

    @Test
    void sinComprasActivasDevuelveUnaListaVacia() {
        when(listPurchases.list(TransactionMother.USER_ID)).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    @Test
    void laEdicionPasaElParcheConvertido() {
        when(updatePurchase.update(eq(TransactionMother.USER_ID), eq(COMPRA_ID), any()))
                .thenReturn(Mono.just(InstallmentMother.televisorSinPagar()));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + COMPRA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"scope\": \"future\", \"categoryId\": \"" + TransactionMother.AMBAS_ID + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(COMPRA_ID.toString())
                .jsonPath("$.installments").doesNotExist();

        ArgumentCaptor<UpdateInstallmentPurchaseCommand> parche =
                ArgumentCaptor.forClass(UpdateInstallmentPurchaseCommand.class);
        verify(updatePurchase).update(eq(TransactionMother.USER_ID), eq(COMPRA_ID), parche.capture());
        assertThat(parche.getValue().scope()).isEqualTo(GroupScope.FUTURE);
        assertThat(parche.getValue().categoryId()).isEqualTo(TransactionMother.AMBAS_ID.toString());
    }

    @Test
    void unParcheSoloConElAlcanceEsErrorSobreBody() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + COMPRA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"scope\": \"ALL\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("body");

        verifyNoInteractions(updatePurchase);
    }

    @Test
    void unParcheSinAlcanceEsErrorSobreScope() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + COMPRA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"description\": \"TV\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("scope");

        verifyNoInteractions(updatePurchase);
    }

    @Test
    void laEdicionConUnIdMalFormadoEsErrorSobreId() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/no-es-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"scope\": \"ALL\", \"description\": \"TV\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("id");
    }

    @Test
    void laCancelacionRespondeSinContenido() {
        when(cancelPurchase.cancel(TransactionMother.USER_ID, COMPRA_ID)).thenReturn(Mono.empty());

        webTestClient.mutateWith(tokenDelUsuario()).delete().uri(URI_BASE + "/" + COMPRA_ID)
                .exchange()
                .expectStatus().isNoContent();

        verify(cancelPurchase).cancel(TransactionMother.USER_ID, COMPRA_ID);
    }

    @Test
    void laCancelacionConUnIdMalFormadoEsErrorSobreId() {
        webTestClient.mutateWith(tokenDelUsuario()).delete().uri(URI_BASE + "/no-es-uuid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verifyNoInteractions(cancelPurchase);
    }

    @Test
    void sinCredencialesEs401() {
        webTestClient.get().uri(URI_BASE)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(listPurchases);
    }
}
