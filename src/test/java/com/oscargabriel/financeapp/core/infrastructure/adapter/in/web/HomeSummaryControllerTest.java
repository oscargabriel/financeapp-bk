package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import static com.oscargabriel.financeapp.core.support.AccountMother.efectivo;
import static com.oscargabriel.financeapp.core.support.AccountMother.visa;
import static com.oscargabriel.financeapp.core.support.BalanceMother.sumasDelEscenario;
import static com.oscargabriel.financeapp.core.support.MonthlySpendingMother.unMes;
import static com.oscargabriel.financeapp.core.support.ReportMother.MERCADO_ID;
import static com.oscargabriel.financeapp.core.support.ReportMother.USER_ID;
import static com.oscargabriel.financeapp.core.support.ReportMother.unGasto;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.compartido.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.compartido.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.compartido.support.SesionSiempreAbierta;
import com.oscargabriel.financeapp.core.domain.model.HomeSummary;
import com.oscargabriel.financeapp.core.domain.model.ReportedTransaction;
import com.oscargabriel.financeapp.core.domain.port.in.GetHomeSummaryPort;

import reactor.core.publisher.Mono;

/** Sin el base-path /api, como los demas slices: la ruta completa la verifica HomeSummaryIT. */
@WebFluxTest(HomeSummaryController.class)
@Import({SecurityConfig.class, JwtConfig.class, SesionSiempreAbierta.class})
class HomeSummaryControllerTest {

    private static final String RUTA = "/reports/home";
    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GetHomeSummaryPort getHomeSummary;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(USER_ID.toString()));
    }

    private static HomeSummary resumen() {
        ReportedTransaction gasto = unGasto(MERCADO_ID, "Mercado", "1200000", "2026-10-01T13:00:00Z");
        ReportedTransaction otro = unGasto(MERCADO_ID, "Mercado", "300000", "2026-10-02T13:00:00Z");
        return HomeSummary.of(OCTUBRE, sumasDelEscenario(),
                List.of(unMes(OCTUBRE, "1905500", "5300000"), unMes(YearMonth.of(2026, 9), "1820000", "4500000")),
                List.of(otro), List.of(gasto, otro), List.of(efectivo(), visa()));
    }

    @Test
    void devuelveElResumenDelUsuarioDelToken() {
        when(getHomeSummary.get(USER_ID)).thenReturn(Mono.just(resumen()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(RUTA)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.currencyCode").isEqualTo("COP")
                .jsonPath("$.month").isEqualTo("2026-10")
                .jsonPath("$.months.length()").isEqualTo(6)
                .jsonPath("$.months[0].periodMonth").isEqualTo("2026-05")
                .jsonPath("$.months[0].totalSpent").isEqualTo(0)
                .jsonPath("$.months[0].totalIncome").isEqualTo(0)
                .jsonPath("$.months[5].totalSpent").isEqualTo(1905500)
                .jsonPath("$.months[5].totalIncome").isEqualTo(5300000)
                .jsonPath("$.spent").isEqualTo(1905500)
                .jsonPath("$.spentChangePercent").isEqualTo(4.70)
                .jsonPath("$.latestExpenses[0].description").isEqualTo("Gasto")
                .jsonPath("$.latestExpenses[0].categoryName").isEqualTo("Mercado")
                .jsonPath("$.latestExpenses[0].occurredAt").isEqualTo("2026-10-02T13:00:00Z")
                .jsonPath("$.latestExpenses[0].amount").isEqualTo(300000)
                .jsonPath("$.latestExpenses[0].currencyCode").isEqualTo("COP")
                .jsonPath("$.latestExpenses[0].amountBase").isEqualTo(300000)
                .jsonPath("$.latestExpenses[0].shareOfTop").doesNotExist()
                .jsonPath("$.topExpenses[0].shareOfTop").isEqualTo(100.00)
                .jsonPath("$.topExpenses[1].shareOfTop").isEqualTo(25.00)
                .jsonPath("$.debitBalance").isEqualTo(12375000)
                .jsonPath("$.accounts[0].name").isEqualTo("Efectivo")
                .jsonPath("$.accounts[0].type").isEqualTo("CASH")
                .jsonPath("$.accounts[0].creditUsedPercent").isEmpty()
                .jsonPath("$.accounts[1].creditLimit").isEqualTo(5000000)
                .jsonPath("$.accounts[1].currentBalance").isEqualTo(-658000)
                .jsonPath("$.accounts[1].availableCredit").isEqualTo(4342000)
                .jsonPath("$.accounts[1].creditUsedPercent").isEqualTo(13.16);
    }

    @Test
    void sinVariacionElCampoSaleEnNull() {
        when(getHomeSummary.get(USER_ID)).thenReturn(Mono.just(HomeSummary.of(OCTUBRE, sumasDelEscenario(),
                List.of(), List.of(), List.of(), List.of())));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(RUTA)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.spentChangePercent").isEmpty()
                .jsonPath("$.latestExpenses").isEmpty()
                .jsonPath("$.topExpenses").isEmpty()
                .jsonPath("$.accounts").isEmpty();
    }

    @Test
    void ignoraUnParametroDesconocido() {
        when(getHomeSummary.get(USER_ID)).thenReturn(Mono.just(resumen()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(RUTA + "?from=2026-01")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.month").isEqualTo("2026-10");

        verify(getHomeSummary).get(USER_ID);
    }
}
