package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static com.oscargabriel.financeapp.support.RecurrenceMother.SERIE_ID;
import static com.oscargabriel.financeapp.support.RecurrenceMother.unaSerie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.model.CreateRecurrenceCommand;
import com.oscargabriel.financeapp.domain.model.Frequency;
import com.oscargabriel.financeapp.domain.model.RecurrenceScope;
import com.oscargabriel.financeapp.domain.model.RecurrenceView;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UpdateRecurrenceCommand;
import com.oscargabriel.financeapp.domain.port.in.CancelRecurrencePort;
import com.oscargabriel.financeapp.domain.port.in.CreateRecurrencePort;
import com.oscargabriel.financeapp.domain.port.in.ListRecurrencesPort;
import com.oscargabriel.financeapp.domain.port.in.UpdateRecurrencePort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre RecurrencesIT. */
@WebFluxTest(RecurrenceController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class RecurrenceControllerTest {

    private static final String URI_BASE = "/recurrences";

    private static final String ALTA = """
            {"type": "EXPENSE", "accountId": "30000000-0000-7000-8000-000000000001",
             "categoryId": "40000000-0000-7000-8000-000000000001", "amount": 44900, "description": "Netflix",
             "frequency": "MONTHLY", "dayOfMonth": 15, "startDate": "2026-10-15", "occurrences": 3}
            """;

    private static final Instant PROXIMA = Instant.parse("2026-10-15T05:00:00Z");

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreateRecurrencePort createRecurrence;

    @MockitoBean
    private ListRecurrencesPort listRecurrences;

    @MockitoBean
    private UpdateRecurrencePort updateRecurrence;

    @MockitoBean
    private CancelRecurrencePort cancelRecurrence;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(TransactionMother.USER_ID.toString()));
    }

    private static RecurrenceView mensual() {
        return new RecurrenceView(unaSerie().veces(3).creadas(3).build(), PROXIMA);
    }

    @Test
    void elAltaRespondeCreadaConLaSerie() {
        when(createRecurrence.create(eq(TransactionMother.USER_ID), any())).thenReturn(Mono.just(mensual()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ALTA)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(SERIE_ID.toString())
                .jsonPath("$.type").isEqualTo("EXPENSE")
                .jsonPath("$.accountId").isEqualTo(TransactionMother.ORIGEN_ID.toString())
                .jsonPath("$.categoryId").isEqualTo(TransactionMother.MERCADO_ID.toString())
                .jsonPath("$.amount").isEqualTo(44900)
                .jsonPath("$.currencyCode").isEqualTo("COP")
                .jsonPath("$.description").isEqualTo("Netflix")
                .jsonPath("$.frequency").isEqualTo("MONTHLY")
                .jsonPath("$.interval").isEqualTo(1)
                .jsonPath("$.dayOfWeek").isEqualTo(null)
                .jsonPath("$.dayOfMonth").isEqualTo(15)
                .jsonPath("$.startDate").isEqualTo("2026-10-09")
                .jsonPath("$.endDate").isEqualTo(null)
                .jsonPath("$.occurrences").isEqualTo(3)
                .jsonPath("$.nextOccurrenceAt").isEqualTo("2026-10-15T05:00:00Z")
                .jsonPath("$.userId").doesNotExist()
                .jsonPath("$.generatedCount").doesNotExist();

        ArgumentCaptor<CreateRecurrenceCommand> alta = ArgumentCaptor.forClass(CreateRecurrenceCommand.class);
        verify(createRecurrence).create(eq(TransactionMother.USER_ID), alta.capture());
        assertThat(alta.getValue().type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(alta.getValue().startDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(alta.getValue().interval()).isEqualTo(1);
    }

    @Test
    void unAltaInvalidaNoLlegaAlCasoDeUso() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ALTA.replace("\"dayOfMonth\": 15", "\"dayOfMonth\": 32"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("dayOfMonth");

        verifyNoInteractions(createRecurrence);
    }

    @Test
    void unaSemanalDevuelveElDiaDeLaSemana() {
        RecurrenceView semanal = new RecurrenceView(
                unaSerie().semanal(2, DayOfWeek.FRIDAY, LocalDate.of(2026, 10, 9)).build(), null);
        when(listRecurrences.list(TransactionMother.USER_ID)).thenReturn(Flux.just(semanal, mensual()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].frequency").isEqualTo("WEEKLY")
                .jsonPath("$[0].interval").isEqualTo(2)
                .jsonPath("$[0].dayOfWeek").isEqualTo("FRIDAY")
                .jsonPath("$[0].dayOfMonth").isEqualTo(null)
                .jsonPath("$[0].nextOccurrenceAt").isEqualTo(null)
                .jsonPath("$[1].id").isEqualTo(SERIE_ID.toString());
    }

    @Test
    void sinSeriesActivasDevuelveUnaListaVacia() {
        when(listRecurrences.list(TransactionMother.USER_ID)).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    @Test
    void laEdicionPasaElParcheConvertido() {
        when(updateRecurrence.update(eq(TransactionMother.USER_ID), eq(SERIE_ID), any()))
                .thenReturn(Mono.just(mensual()));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + SERIE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"scope\": \"future\", \"frequency\": \"weekly\", \"dayOfWeek\": \"friday\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(SERIE_ID.toString());

        ArgumentCaptor<UpdateRecurrenceCommand> parche = ArgumentCaptor.forClass(UpdateRecurrenceCommand.class);
        verify(updateRecurrence).update(eq(TransactionMother.USER_ID), eq(SERIE_ID), parche.capture());
        assertThat(parche.getValue().scope()).isEqualTo(RecurrenceScope.FUTURE);
        assertThat(parche.getValue().frequency()).isEqualTo(Frequency.WEEKLY);
        assertThat(parche.getValue().dayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    }

    @Test
    void unParcheSoloConElAlcanceEsErrorSobreBody() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + SERIE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"scope\": \"ALL\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("body");

        verifyNoInteractions(updateRecurrence);
    }

    @Test
    void unParcheSinAlcanceEsErrorSobreScope() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + SERIE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\": 35000}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("scope");

        verifyNoInteractions(updateRecurrence);
    }

    @Test
    void laEdicionConUnIdMalFormadoEsErrorSobreId() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/no-es-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"scope\": \"ALL\", \"amount\": 1}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("id");
    }

    @Test
    void laCancelacionRespondeSinContenido() {
        when(cancelRecurrence.cancel(TransactionMother.USER_ID, SERIE_ID)).thenReturn(Mono.empty());

        webTestClient.mutateWith(tokenDelUsuario()).delete().uri(URI_BASE + "/" + SERIE_ID)
                .exchange()
                .expectStatus().isNoContent();

        verify(cancelRecurrence).cancel(TransactionMother.USER_ID, SERIE_ID);
    }

    @Test
    void laCancelacionConUnIdMalFormadoEsErrorSobreId() {
        webTestClient.mutateWith(tokenDelUsuario()).delete().uri(URI_BASE + "/no-es-uuid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verifyNoInteractions(cancelRecurrence);
    }

    @Test
    void sinCredencialesEs401() {
        webTestClient.get().uri(URI_BASE)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(listRecurrences);
    }
}
