package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import com.oscargabriel.financeapp.domain.model.AssistantReply;
import com.oscargabriel.financeapp.domain.model.Balance;
import com.oscargabriel.financeapp.domain.model.TransactionReport;
import com.oscargabriel.financeapp.domain.port.in.AssistantPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.BalanceMother;
import com.oscargabriel.financeapp.support.BasicMother;
import com.oscargabriel.financeapp.support.ReportMother;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Mono;

/** Sin el base-path /api, como los demas slices: bruno/assistant/ prueba la ruta completa. */
@WebFluxTest(AssistantController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class AssistantControllerTest {

    private static final String RUTA = "/assistant/messages";
    private static final String TEXTO = "gaste 20 mil en el almuerzo";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AssistantPort assistant;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(TransactionMother.USER_ID.toString()));
    }

    private WebTestClient.ResponseSpec envia(Object cuerpo) {
        return webTestClient.mutateWith(tokenDelUsuario()).post().uri(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cuerpo)
                .exchange();
    }

    private void responde(AssistantReply respuesta) {
        when(assistant.atender(eq(TransactionMother.USER_ID), anyString())).thenReturn(Mono.just(respuesta));
    }

    static Stream<Arguments> datosInvalidos() {
        return Stream.of(
                Arguments.of(Map.of()),
                Arguments.of(Map.of("data", "")),
                Arguments.of(Map.of("data", "   ")),
                Arguments.of(Map.of("data", "x".repeat(1001))));
    }

    @ParameterizedTest
    @MethodSource("datosInvalidos")
    void unDataAusenteEnBlancoOSobreElTopeEsUn400SinLlamarAlAsistente(Map<String, String> cuerpo) {
        envia(cuerpo)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("data");

        verifyNoInteractions(assistant);
    }

    @Test
    void milCaracteresSeAceptanYPasanTalCualConElUsuarioDelToken() {
        responde(AssistantReply.noSoportado("No soportado"));
        String mil = "x".repeat(1000);

        envia(Map.of("data", mil)).expectStatus().isOk();

        verify(assistant).atender(TransactionMother.USER_ID, mil);
    }

    @Test
    void sinTokenEsUn401() {
        webTestClient.post().uri(RUTA).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("data", TEXTO))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer");

        verifyNoInteractions(assistant);
    }

    @Test
    void conLaCredencialBasicEsUn401() {
        webTestClient.post().uri(RUTA).headers(BasicMother.cabecera()).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("data", TEXTO))
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(assistant);
    }

    @Test
    void unMovimientoCreadoSaleConElContratoDelAltaYSinReporteNiSaldo() {
        responde(AssistantReply.creado("Registre un gasto. Queda pendiente de tu aprobacion.",
                TransactionMother.unGastoPendiente()));

        envia(Map.of("data", TEXTO))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.intent").isEqualTo("CREATE_TRANSACTION")
                .jsonPath("$.message").isEqualTo("Registre un gasto. Queda pendiente de tu aprobacion.")
                .jsonPath("$.transaction.id").isEqualTo(TransactionMother.GASTO_GUARDADO_ID.toString())
                .jsonPath("$.transaction.status").isEqualTo("PENDING")
                .jsonPath("$.transaction.origin").isEqualTo("TELEGRAM")
                .jsonPath("$.transaction.userId").doesNotExist()
                .jsonPath("$.report").isEqualTo(null)
                .jsonPath("$.balance").isEqualTo(null);
    }

    @Test
    void unReporteSaleConElContratoDeReportsTransactions() {
        responde(AssistantReply.reporte("Del 2026-09-01 al 2026-09-30",
                TransactionReport.of("COP", ReportMother.sinFiltros(), List.of())));

        envia(Map.of("data", TEXTO))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.intent").isEqualTo("LIST_TRANSACTIONS")
                .jsonPath("$.report.from").isEqualTo("2026-09-01")
                .jsonPath("$.report.to").isEqualTo("2026-09-30")
                .jsonPath("$.report.transactions").isArray()
                .jsonPath("$.transaction").isEqualTo(null)
                .jsonPath("$.balance").isEqualTo(null);
    }

    @Test
    void unSaldoSaleConElContratoDeReportsBalance() {
        responde(AssistantReply.saldo("Neto del mes", Balance.of(BalanceMother.DESDE, BalanceMother.HASTA,
                BalanceMother.sumasDelEscenario(), List.of())));

        envia(Map.of("data", TEXTO))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.intent").isEqualTo("GET_BALANCE")
                .jsonPath("$.balance.currencyCode").isEqualTo("COP")
                .jsonPath("$.transaction").isEqualTo(null)
                .jsonPath("$.report").isEqualTo(null);
    }

    @Test
    void unaAclaracionSaleSinNingunDato() {
        responde(AssistantReply.aclaracion("No encontre la cuenta «Bancolombia»."));

        envia(Map.of("data", TEXTO))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.intent").isEqualTo("NEEDS_CLARIFICATION")
                .jsonPath("$.message").isEqualTo("No encontre la cuenta «Bancolombia».")
                .jsonPath("$.transaction").isEqualTo(null)
                .jsonPath("$.report").isEqualTo(null)
                .jsonPath("$.balance").isEqualTo(null);
    }

    @Test
    void unaFallaDelModeloEsUn502ConElFormatoComun() {
        when(assistant.atender(eq(TransactionMother.USER_ID), anyString()))
                .thenReturn(Mono.error(new BadRequestException(HttpStatus.BAD_GATEWAY,
                        ErrorCodes.EXTERNAL_SERVICE_ERROR, "El asistente no esta disponible", "server")));

        envia(Map.of("data", TEXTO))
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("EXTERNAL_SERVICE_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("server");
    }
}
