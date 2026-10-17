package com.oscargabriel.financeapp.ia.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.ia.domain.model.AssistantBalance;
import com.oscargabriel.financeapp.ia.domain.model.AssistantChannel;
import com.oscargabriel.financeapp.ia.domain.model.AssistantFile;
import com.oscargabriel.financeapp.ia.domain.model.AssistantFileType;
import com.oscargabriel.financeapp.ia.domain.model.AssistantMessage;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReply;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReport;
import com.oscargabriel.financeapp.ia.domain.model.CreatedGroup;
import com.oscargabriel.financeapp.ia.domain.model.CreatedMovement;
import com.oscargabriel.financeapp.ia.domain.model.TransactionType;
import com.oscargabriel.financeapp.ia.domain.port.in.AssistantPort;
import com.oscargabriel.financeapp.compartido.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.compartido.support.SesionSiempreAbierta;
import com.oscargabriel.financeapp.compartido.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.compartido.support.BasicMother;
import com.oscargabriel.financeapp.ia.support.AsistenteMother;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import reactor.core.publisher.Mono;

/** Sin el base-path /api, como los demas slices: bruno/assistant/ prueba la ruta completa. */
@WebFluxTest(AssistantController.class)
@Import({SecurityConfig.class, JwtConfig.class, SesionSiempreAbierta.class})
class AssistantControllerTest {

    private static final String RUTA = "/assistant/messages";
    private static final String TEXTO = "gaste 20 mil en el almuerzo";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AssistantPort assistant;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(AsistenteMother.USER_ID.toString()));
    }

    private WebTestClient.ResponseSpec envia(Object cuerpo) {
        return webTestClient.mutateWith(tokenDelUsuario()).post().uri(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cuerpo)
                .exchange();
    }

    private void responde(AssistantReply respuesta) {
        when(assistant.atender(eq(AsistenteMother.USER_ID), eq(AssistantChannel.WEB_CHAT), any(AssistantMessage.class))).thenReturn(Mono.just(respuesta));
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

        verify(assistant).atender(AsistenteMother.USER_ID, AssistantChannel.WEB_CHAT, AssistantMessage.deTexto(mil));
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
    void elGrupoCreadoSaleConSuIdYSusMovimientosLivianosSinReporteNiSaldo() {
        UUID id = UUID.fromString("30000000-0000-7000-8000-000000000001");
        UUID grupo = UUID.fromString("71000000-0000-7000-8000-000000000001");
        responde(AssistantReply.creado("Registre un gasto. Confirmalo o descartalo.", new CreatedGroup(grupo, List.of(
                new CreatedMovement(id, TransactionType.EXPENSE, "PENDING", new BigDecimal("20000"), "COP",
                        "Almuerzo", Instant.parse("2026-10-08T15:00:00Z"), "Efectivo", null, "Mercado")))));

        envia(Map.of("data", TEXTO))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.intent").isEqualTo("CREATE_TRANSACTION")
                .jsonPath("$.message").isEqualTo("Registre un gasto. Confirmalo o descartalo.")
                .jsonPath("$.transaction").doesNotExist()
                .jsonPath("$.group.id").isEqualTo(grupo.toString())
                .jsonPath("$.group.transactions.length()").isEqualTo(1)
                .jsonPath("$.group.transactions[0].id").isEqualTo(id.toString())
                .jsonPath("$.group.transactions[0].type").isEqualTo("EXPENSE")
                .jsonPath("$.group.transactions[0].status").isEqualTo("PENDING")
                .jsonPath("$.group.transactions[0].amount").isEqualTo(20000)
                .jsonPath("$.group.transactions[0].occurredAt").isEqualTo("2026-10-08T15:00:00Z")
                .jsonPath("$.group.transactions[0].account").isEqualTo("Efectivo")
                .jsonPath("$.group.transactions[0].destinationAccount").isEqualTo(null)
                .jsonPath("$.group.transactions[0].category").isEqualTo("Mercado")
                .jsonPath("$.group.transactions[0].accountId").doesNotExist()
                .jsonPath("$.group.transactions[0].origin").doesNotExist()
                .jsonPath("$.report").isEqualTo(null)
                .jsonPath("$.balance").isEqualTo(null);
    }

    @Test
    void unReporteSaleLivianoConLosTotalesYLaListaSinAmountBase() {
        AssistantReport.Item gasto = AsistenteMother.unMovimiento(TransactionType.EXPENSE, "10", "40000");
        responde(AssistantReply.reporte("Del 2026-09-01 al 2026-09-30", AssistantReport.of(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "COP", List.of(gasto))));

        envia(Map.of("data", TEXTO))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.intent").isEqualTo("LIST_TRANSACTIONS")
                .jsonPath("$.report.from").isEqualTo("2026-09-01")
                .jsonPath("$.report.to").isEqualTo("2026-09-30")
                .jsonPath("$.report.currencyCode").isEqualTo("COP")
                .jsonPath("$.report.income").isEqualTo(0)
                .jsonPath("$.report.expense").isEqualTo(40000)
                .jsonPath("$.report.net").isEqualTo(-40000)
                .jsonPath("$.report.transactions[0].id").isEqualTo(gasto.id().toString())
                .jsonPath("$.report.transactions[0].amount").isEqualTo(10)
                .jsonPath("$.report.transactions[0].currencyCode").isEqualTo("USD")
                .jsonPath("$.report.transactions[0].account").isEqualTo("Ahorros USD")
                .jsonPath("$.report.transactions[0].category").isEqualTo("Mercado")
                .jsonPath("$.report.transactions[0].amountBase").doesNotExist()
                .jsonPath("$.report.totalsByType").doesNotExist()
                .jsonPath("$.group").isEqualTo(null)
                .jsonPath("$.balance").isEqualTo(null);
    }

    @Test
    void unSaldoSaleLivianoConLasCuentasPorNombre() {
        responde(AssistantReply.saldo("Neto del mes", AssistantBalance.of(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), AsistenteMother.sumas(), List.of(AsistenteMother.saldoDeEfectivo()))));

        envia(Map.of("data", TEXTO))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.intent").isEqualTo("GET_BALANCE")
                .jsonPath("$.balance.from").isEqualTo("2026-10-01")
                .jsonPath("$.balance.currencyCode").isEqualTo("COP")
                .jsonPath("$.balance.income").isEqualTo(3000000)
                .jsonPath("$.balance.expense").isEqualTo(1200000)
                .jsonPath("$.balance.net").isEqualTo(1800000)
                .jsonPath("$.balance.allTimeNet").isEqualTo(1500000)
                .jsonPath("$.balance.accounts[0].name").isEqualTo("Efectivo")
                .jsonPath("$.balance.accounts[0].currentBalance").isEqualTo(250000)
                .jsonPath("$.balance.accounts[0].id").doesNotExist()
                .jsonPath("$.balance.accounts[0].availableCredit").doesNotExist()
                .jsonPath("$.balance.period").doesNotExist()
                .jsonPath("$.group").isEqualTo(null)
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
                .jsonPath("$.group").isEqualTo(null)
                .jsonPath("$.report").isEqualTo(null)
                .jsonPath("$.balance").isEqualTo(null);
    }

    @Test
    void unaFallaDelModeloEsUn502ConElFormatoComun() {
        when(assistant.atender(eq(AsistenteMother.USER_ID), eq(AssistantChannel.WEB_CHAT), any(AssistantMessage.class)))
                .thenReturn(Mono.error(new BadRequestException(HttpStatus.BAD_GATEWAY,
                        ErrorCodes.EXTERNAL_SERVICE_ERROR, "El asistente no esta disponible", "server")));

        envia(Map.of("data", TEXTO))
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("EXTERNAL_SERVICE_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("server");
    }

    /** FIN-57: el mismo endpoint con multipart/form-data, un archivo y un texto opcional. */
    @Nested
    class ConArchivo {

        private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

        private static byte[] jpeg(int tamano, String relleno) {
            byte[] contenido = new byte[tamano];
            byte[] marca = relleno.getBytes(StandardCharsets.US_ASCII);
            for (int i = FIRMA_JPEG.length; i + marca.length <= tamano; i += marca.length) {
                System.arraycopy(marca, 0, contenido, i, marca.length);
            }
            System.arraycopy(FIRMA_JPEG, 0, contenido, 0, FIRMA_JPEG.length);
            return contenido;
        }

        private static MultipartBodyBuilder conArchivo(byte[] contenido, MediaType tipo) {
            MultipartBodyBuilder cuerpo = new MultipartBodyBuilder();
            cuerpo.part("file", new ByteArrayResource(contenido) {
                @Override
                public String getFilename() {
                    return "recibo.jpg";
                }
            }).contentType(tipo);
            return cuerpo;
        }

        private WebTestClient.RequestHeadersSpec<?> peticion(MultipartBodyBuilder cuerpo) {
            return webTestClient.post().uri(RUTA)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(cuerpo.build()));
        }

        private WebTestClient.ResponseSpec envia(MultipartBodyBuilder cuerpo) {
            return webTestClient.mutateWith(tokenDelUsuario()).post().uri(RUTA)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(cuerpo.build()))
                    .exchange();
        }

        @Test
        void elArchivoYElTextoLleganAlAsistenteConElUsuarioDelToken() {
            responde(AssistantReply.noSoportado("No soportado"));
            byte[] recibo = jpeg(64, "x");
            MultipartBodyBuilder cuerpo = conArchivo(recibo, MediaType.IMAGE_JPEG);
            cuerpo.part("data", "con la Nequi");
            ArgumentCaptor<AssistantMessage> recibido = ArgumentCaptor.forClass(AssistantMessage.class);

            envia(cuerpo).expectStatus().isOk().expectBody().jsonPath("$.intent").isEqualTo("UNSUPPORTED");

            verify(assistant).atender(eq(AsistenteMother.USER_ID), eq(AssistantChannel.WEB_CHAT), recibido.capture());
            assertThat(recibido.getValue().texto()).isEqualTo("con la Nequi");
            assertThat(recibido.getValue().archivo().tipo()).isEqualTo(AssistantFileType.JPEG);
            assertThat(recibido.getValue().archivo().contenido()).containsExactly(recibo);
        }

        @Test
        void soloConTextoEsComoElJson() {
            responde(AssistantReply.noSoportado("No soportado"));
            MultipartBodyBuilder cuerpo = new MultipartBodyBuilder();
            cuerpo.part("data", TEXTO);

            envia(cuerpo).expectStatus().isOk();

            verify(assistant).atender(AsistenteMother.USER_ID, AssistantChannel.WEB_CHAT, AssistantMessage.deTexto(TEXTO));
        }

        /** El codec corta los cuerpos en memoria por encima de su tope; el archivo llega en trozos y no lo toca. */
        @Test
        void unArchivoDeExactamenteCincoMegasPasa() {
            responde(AssistantReply.noSoportado("No soportado"));

            envia(conArchivo(jpeg(AssistantFile.TOPE_BYTES, "x"), MediaType.IMAGE_JPEG)).expectStatus().isOk();

            verify(assistant).atender(eq(AsistenteMother.USER_ID), eq(AssistantChannel.WEB_CHAT), any(AssistantMessage.class));
        }

        @Test
        void unByteSobreLosCincoMegasEsUn400SinLlamarAlAsistente() {
            envia(conArchivo(jpeg(AssistantFile.TOPE_BYTES + 1, "x"), MediaType.IMAGE_JPEG))
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                    .jsonPath("$.errors[0].field").isEqualTo("file");

            verifyNoInteractions(assistant);
        }

        @Test
        void unVideoEsUn400SinLlamarAlAsistente() {
            envia(conArchivo(jpeg(64, "x"), MediaType.parseMediaType("video/mp4")))
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                    .jsonPath("$.errors[0].field").isEqualTo("file");

            verifyNoInteractions(assistant);
        }

        @Test
        void sinTokenEsUn401() {
            peticion(conArchivo(jpeg(64, "x"), MediaType.IMAGE_JPEG)).exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().valueEquals("WWW-Authenticate", "Bearer");

            verifyNoInteractions(assistant);
        }

        @Test
        void conLaCredencialBasicEsUn401() {
            peticion(conArchivo(jpeg(64, "x"), MediaType.IMAGE_JPEG)).headers(BasicMother.cabecera()).exchange()
                    .expectStatus().isUnauthorized();

            verifyNoInteractions(assistant);
        }

        @Test
        void unArchivoRechazadoNoDejaSuContenidoEnElLog() {
            String marca = "MARCA-DEL-RECIBO-";
            byte[] contenido = jpeg(4096, marca);
            ListAppender<ILoggingEvent> logs = new ListAppender<>();
            logs.start();
            Logger raiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            raiz.addAppender(logs);
            try {
                envia(conArchivo(contenido, MediaType.parseMediaType("video/mp4"))).expectStatus().isBadRequest();
                envia(conArchivo(jpeg(AssistantFile.TOPE_BYTES + 1, marca), MediaType.IMAGE_JPEG))
                        .expectStatus().isBadRequest();
            } finally {
                raiz.detachAppender(logs);
            }

            String base64 = Base64.getEncoder().encodeToString(Arrays.copyOfRange(contenido, 0, 30));
            assertThat(logs.list).allSatisfy(evento -> assertThat(evento.getFormattedMessage())
                    .doesNotContain(marca)
                    .doesNotContain(base64.substring(8, 24)));
        }
    }
}
