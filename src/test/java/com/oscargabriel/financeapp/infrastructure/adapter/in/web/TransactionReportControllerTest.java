package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static com.oscargabriel.financeapp.support.ReportMother.AHORA;
import static com.oscargabriel.financeapp.support.ReportMother.CUENTA_ID;
import static com.oscargabriel.financeapp.support.ReportMother.DESDE;
import static com.oscargabriel.financeapp.support.ReportMother.DESTINO_ID;
import static com.oscargabriel.financeapp.support.ReportMother.HASTA;
import static com.oscargabriel.financeapp.support.ReportMother.MERCADO_ID;
import static com.oscargabriel.financeapp.support.ReportMother.RESTAURANTES_ID;
import static com.oscargabriel.financeapp.support.ReportMother.SALARIO_ID;
import static com.oscargabriel.financeapp.support.ReportMother.USER_ID;
import static com.oscargabriel.financeapp.support.ReportMother.deLaSerie;
import static com.oscargabriel.financeapp.support.ReportMother.sinFiltros;
import static com.oscargabriel.financeapp.support.ReportMother.unGasto;
import static com.oscargabriel.financeapp.support.ReportMother.unaTransferenciaEnDolares;
import static com.oscargabriel.financeapp.support.AccountMother.efectivo;
import static com.oscargabriel.financeapp.support.AccountMother.visa;
import static com.oscargabriel.financeapp.support.BalanceMother.sumasDelEscenario;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.model.Balance;
import com.oscargabriel.financeapp.domain.model.TransactionReport;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.in.GetBalancePort;
import com.oscargabriel.financeapp.domain.port.in.GetTransactionReportPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;

import reactor.core.publisher.Mono;

/** Sin el base-path /api, como los demas slices: la ruta completa la verifica TransactionReportIT. */
@WebFluxTest(TransactionReportController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class TransactionReportControllerTest {

    private static final String RUTA = "/reports/transactions";
    private static final String RANGO = RUTA + "?from=2026-09-01&to=2026-09-30";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private GetTransactionReportPort getTransactionReport;

    @MockitoBean
    private GetBalancePort getBalance;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(USER_ID.toString()));
    }

    private void respondeVacio() {
        when(getTransactionReport.get(eq(USER_ID), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(TransactionReport.of("COP", sinFiltros(), List.of(), AHORA)));
    }

    @Test
    void sinFiltrosPasaElRangoYTresConjuntosVacios() {
        respondeVacio();

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(RANGO)
                .exchange()
                .expectStatus().isOk();

        verify(getTransactionReport).get(USER_ID, DESDE, HASTA, Set.of(), Set.of(), Set.of());
    }

    @Test
    void aceptaLosFiltrosRepetidosOSeparadosPorComaYIgnoraLosBlancosYLasMayusculas() {
        respondeVacio();

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(RANGO
                        + "&categoryId=" + MERCADO_ID + "," + SALARIO_ID
                        + "&categoryId=" + RESTAURANTES_ID + "&categoryId="
                        + "&type=expense, Income&type=EXPENSE")
                .exchange()
                .expectStatus().isOk();

        verify(getTransactionReport).get(USER_ID, DESDE, HASTA, Set.of(MERCADO_ID, SALARIO_ID, RESTAURANTES_ID),
                Set.of(), Set.of(TransactionType.EXPENSE, TransactionType.INCOME));
    }

    @Test
    void aceptaLasCuentasRepetidasOSeparadasPorComaYIgnoraLosBlancos() {
        respondeVacio();

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(RANGO
                        + "&accountId=" + CUENTA_ID + ", " + DESTINO_ID
                        + "&accountId=" + CUENTA_ID + "&accountId=")
                .exchange()
                .expectStatus().isOk();

        verify(getTransactionReport).get(USER_ID, DESDE, HASTA, Set.of(), Set.of(CUENTA_ID, DESTINO_ID), Set.of());
    }

    @Test
    void devuelveElReporteConElFormatoDelContrato() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), List.of(
                unGasto(MERCADO_ID, "Mercado", "85000.0000", "2026-09-02T15:00:00Z"),
                unaTransferenciaEnDolares("2026-09-10T20:00:00Z")), AHORA);
        when(getTransactionReport.get(eq(USER_ID), any(), any(), any(), any(), any())).thenReturn(Mono.just(reporte));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(RANGO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.from").isEqualTo("2026-09-01")
                .jsonPath("$.to").isEqualTo("2026-09-30")
                .jsonPath("$.currencyCode").isEqualTo("COP")
                .jsonPath("$.transactions.length()").isEqualTo(2)
                .jsonPath("$.transactions[0].type").isEqualTo("TRANSFER")
                .jsonPath("$.transactions[0].categoryId").isEqualTo(null)
                .jsonPath("$.transactions[0].categoryName").isEqualTo(null)
                .jsonPath("$.transactions[0].amount").isEqualTo(100.0)
                .jsonPath("$.transactions[0].currencyCode").isEqualTo("USD")
                .jsonPath("$.transactions[0].amountBase").isEqualTo(410000.0)
                .jsonPath("$.transactions[0].occurredAt").isEqualTo("2026-09-10T20:00:00Z")
                .jsonPath("$.transactions[1].categoryId").isEqualTo(MERCADO_ID.toString())
                .jsonPath("$.transactions[1].categoryName").isEqualTo("Mercado")
                .jsonPath("$.transactions[1].destinationAccountId").isEqualTo(null)
                .jsonPath("$.totalsByType[0].type").isEqualTo("EXPENSE")
                .jsonPath("$.totalsByType[0].total").isEqualTo(85000.0)
                .jsonPath("$.totalsByType[0].count").isEqualTo(1)
                .jsonPath("$.totalsByType[2].type").isEqualTo("TRANSFER")
                .jsonPath("$.totalsByCategory.length()").isEqualTo(1)
                .jsonPath("$.totalsByCategory[0].categoryName").isEqualTo("Mercado")
                .jsonPath("$.net").isEqualTo(-85000.0)
                .jsonPath("$.userId").doesNotExist();
    }

    @Test
    void marcaCadaMovimientoProgramadoSegunElInstanteDelReporte() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), List.of(
                unGasto(MERCADO_ID, "Mercado", "85000.0000", "2026-09-02T15:00:00Z"),
                unGasto(MERCADO_ID, "Mercado", "40000.0000", "2026-10-15T15:00:00Z")), AHORA);
        when(getTransactionReport.get(eq(USER_ID), any(), any(), any(), any(), any())).thenReturn(Mono.just(reporte));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(RANGO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.transactions[0].occurredAt").isEqualTo("2026-10-15T15:00:00Z")
                .jsonPath("$.transactions[0].scheduled").isEqualTo(true)
                .jsonPath("$.transactions[1].scheduled").isEqualTo(false)
                .jsonPath("$.totalsByType[0].total").isEqualTo(85000.0)
                .jsonPath("$.net").isEqualTo(-85000.0);
    }

    @Test
    void cadaMovimientoTraeLaSerieDeLaQueEsOcurrencia() {
        UUID serie = UUID.fromString("80000000-0000-7000-8000-000000000001");
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), List.of(
                unGasto(MERCADO_ID, "Mercado", "85000.0000", "2026-09-02T15:00:00Z"),
                deLaSerie(unGasto(MERCADO_ID, "Mercado", "44900.0000", "2026-09-15T05:00:00Z"), serie)),
                AHORA);
        when(getTransactionReport.get(eq(USER_ID), any(), any(), any(), any(), any())).thenReturn(Mono.just(reporte));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(RANGO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.transactions[0].recurrenceId").isEqualTo(serie.toString())
                .jsonPath("$.transactions[1].recurrenceId").isEqualTo(null);
    }

    static Stream<Arguments> unParametroInvalido() {
        return Stream.of(
                Arguments.of(RUTA + "?to=2026-09-30", "from"),
                Arguments.of(RUTA + "?from=2026-09-01", "to"),
                Arguments.of(RUTA + "?from=&to=2026-09-30", "from"),
                Arguments.of(RUTA + "?from=2026-09-01&to=2026-10-1", "to"),
                Arguments.of(RUTA + "?from=2026/09/01&to=2026-09-30", "from"),
                Arguments.of(RUTA + "?from=01-09-2026&to=2026-09-30", "from"),
                Arguments.of(RUTA + "?from=2026-02-30&to=2026-03-31", "from"),
                Arguments.of(RANGO + "&categoryId=abc", "categoryId"),
                Arguments.of(RANGO + "&categoryId=" + MERCADO_ID + ",abc", "categoryId"),
                Arguments.of(RANGO + "&accountId=abc", "accountId"),
                Arguments.of(RANGO + "&accountId=" + CUENTA_ID + ",abc", "accountId"),
                Arguments.of(RANGO + "&type=PAGO", "type"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("unParametroInvalido")
    void unParametroInvalidoEs400EnSuCampoSinConsultar(String uri, String campo) {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(uri)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo(campo);

        verifyNoInteractions(getTransactionReport);
    }

    @Test
    void devuelve401SinCredenciales() {
        webTestClient.get().uri(RANGO)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(getTransactionReport);
    }

    private static final String SALDO = "/reports/balance";
    private static final LocalDate PRIMERO = LocalDate.of(2026, 10, 1);
    private static final LocalDate ULTIMO = LocalDate.of(2026, 10, 31);

    private void saldoDelEscenario() {
        when(getBalance.get(eq(USER_ID), any(), any()))
                .thenReturn(Mono.just(Balance.of(PRIMERO, ULTIMO, sumasDelEscenario(), List.of(efectivo(), visa()))));
    }

    @Test
    void elSaldoSinParametrosLlegaAlPuertoSinRango() {
        saldoDelEscenario();

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(SALDO)
                .exchange()
                .expectStatus().isOk();

        verify(getBalance).get(USER_ID, null, null);
    }

    @Test
    void elSaldoConRangoLlegaAlPuertoConLasFechas() {
        saldoDelEscenario();

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(SALDO + "?from=2026-10-01&to=2026-10-31")
                .exchange()
                .expectStatus().isOk();

        verify(getBalance).get(USER_ID, PRIMERO, ULTIMO);
    }

    @Test
    void elSaldoSinUnoDeLosExtremosLoPasaComoNulo() {
        saldoDelEscenario();

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(SALDO + "?from=2026-10-01")
                .exchange()
                .expectStatus().isOk();

        verify(getBalance).get(USER_ID, PRIMERO, null);
    }

    @Test
    void devuelveElSaldoConElFormatoDelContrato() {
        saldoDelEscenario();

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(SALDO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.currencyCode").isEqualTo("COP")
                .jsonPath("$.period.from").isEqualTo("2026-10-01")
                .jsonPath("$.period.to").isEqualTo("2026-10-31")
                .jsonPath("$.period.income").isEqualTo(5300000.0)
                .jsonPath("$.period.expense").isEqualTo(1905500.0)
                .jsonPath("$.period.net").isEqualTo(3394500.0)
                .jsonPath("$.allTime.income").isEqualTo(14300000.0)
                .jsonPath("$.allTime.expense").isEqualTo(5170500.0)
                .jsonPath("$.allTime.net").isEqualTo(9129500.0)
                .jsonPath("$.allTime.from").doesNotExist()
                .jsonPath("$.accounts.length()").isEqualTo(2)
                .jsonPath("$.accounts[0].name").isEqualTo("Efectivo")
                .jsonPath("$.accounts[0].type").isEqualTo("CASH")
                .jsonPath("$.accounts[0].creditLimit").isEqualTo(null)
                .jsonPath("$.accounts[0].availableCredit").isEqualTo(null)
                .jsonPath("$.accounts[1].id").isEqualTo(visa().id().toString())
                .jsonPath("$.accounts[1].currencyCode").isEqualTo("COP")
                .jsonPath("$.accounts[1].currentBalance").isEqualTo(-658000.0)
                .jsonPath("$.accounts[1].creditLimit").isEqualTo(5000000.0)
                .jsonPath("$.accounts[1].availableCredit").isEqualTo(4342000.0)
                .jsonPath("$.accounts[1].initialBalance").doesNotExist()
                .jsonPath("$.userId").doesNotExist();
    }

    static Stream<Arguments> unaFechaDeSaldoInvalida() {
        return Stream.of(
                Arguments.of(SALDO + "?from=2026/10/01&to=2026-10-31", "from"),
                Arguments.of(SALDO + "?from=2026-02-30&to=2026-03-31", "from"),
                Arguments.of(SALDO + "?from=2026-10-01&to=2026-10-1", "to"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("unaFechaDeSaldoInvalida")
    void unaFechaDeSaldoInvalidaEs400EnSuCampoSinConsultar(String uri, String campo) {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(uri)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo(campo);

        verifyNoInteractions(getBalance);
    }
}
