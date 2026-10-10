package com.oscargabriel.financeapp.infrastructure.adapter.out.exchangerate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

class ExchangeRateApiAdapterTest {

    private static final String RUTA = "/v6/latest/USD";

    private HttpServer servidor;
    private final AtomicReference<HttpExchange> peticion = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String respuesta = "{}";
    private volatile long demoraMs = 0;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void levantarProveedorFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", intercambio -> {
            peticion.set(intercambio);
            try {
                Thread.sleep(demoraMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(status, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(ExchangeRateApiAdapter.class)).addAppender(logs);
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
        ((Logger) LoggerFactory.getLogger(ExchangeRateApiAdapter.class)).detachAppender(logs);
    }

    private String url() {
        return "http://localhost:" + servidor.getAddress().getPort() + RUTA;
    }

    /** Holgado: el primer request de la clase paga el arranque en frio de Netty. */
    private ExchangeRateApiAdapter adapter() {
        return adapterCon(Duration.ofSeconds(5));
    }

    private ExchangeRateApiAdapter adapterCon(Duration timeout) {
        return new ExchangeRateApiAdapter(url(), timeout, JsonMapper.builder().build());
    }

    private void respondeTasas(String tasas) {
        respuesta = """
                {"result":"success","provider":"https://www.exchangerate-api.com","base_code":"USD",
                 "time_last_update_unix":1791590401,"rates":{%s}}""".formatted(tasas);
    }

    @Test
    void pideLaUrlConfiguradaYDevuelveLasTasasPorMoneda() {
        respondeTasas("\"USD\":1,\"COP\":4123.45,\"EUR\":0.8612");

        StepVerifier.create(adapter().latestFromUsd())
                .assertNext(tasas -> {
                    assertThat(tasas).containsOnlyKeys("USD", "COP", "EUR");
                    assertThat(tasas.get("COP")).isEqualByComparingTo("4123.45");
                    assertThat(tasas.get("EUR")).isEqualByComparingTo("0.8612");
                })
                .verifyComplete();

        assertThat(peticion.get().getRequestMethod()).isEqualTo("GET");
        assertThat(peticion.get().getRequestURI().getPath()).isEqualTo(RUTA);
    }

    /** NUMERIC(20,10) no guarda mas de diez digitos enteros, y una tasa tiene que ser positiva. */
    @Test
    void descartaLasTasasFueraDeRangoYLasQueNoSonNumeros() {
        respondeTasas("\"COP\":4000,\"CERO\":0,\"NEG\":-1,\"ENORME\":10000000000,\"TXT\":\"abc\"");

        StepVerifier.create(adapter().latestFromUsd())
                .assertNext(tasas -> assertThat(tasas).containsOnlyKeys("COP"))
                .verifyComplete();
    }

    @Test
    void unResultadoDeErrorEsUnFallo() {
        respuesta = "{\"result\":\"error\",\"error-type\":\"unsupported-code\"}";

        StepVerifier.create(adapter().latestFromUsd())
                .verifyErrorSatisfies(ExchangeRateApiAdapterTest::esProveedorCaido);
    }

    @Test
    void otraMonedaBaseEsUnFallo() {
        respuesta = "{\"result\":\"success\",\"base_code\":\"EUR\",\"rates\":{\"COP\":4500}}";

        StepVerifier.create(adapter().latestFromUsd())
                .verifyErrorSatisfies(ExchangeRateApiAdapterTest::esProveedorCaido);
    }

    @Test
    void unCuerpoIlegibleEsUnFallo() {
        respuesta = "esto no es json";

        StepVerifier.create(adapter().latestFromUsd())
                .verifyErrorSatisfies(ExchangeRateApiAdapterTest::esProveedorCaido);
    }

    @Test
    void unErrorDelProveedorEsUnFalloQueQuedaEnElLogConSuStatus() {
        status = 429;
        respuesta = "{\"result\":\"error\",\"error-type\":\"detalle interno del proveedor\"}";

        StepVerifier.create(adapter().latestFromUsd())
                .verifyErrorSatisfies(ExchangeRateApiAdapterTest::esProveedorCaido);

        assertThat(logs.list).singleElement().satisfies(evento ->
                assertThat(evento.getFormattedMessage()).contains("429"));
    }

    @Test
    void unaRespuestaQueNoLlegaATiempoEsUnFalloQueQuedaEnElLog() {
        demoraMs = 1000;
        respondeTasas("\"COP\":4000");

        StepVerifier.create(adapterCon(Duration.ofMillis(200)).latestFromUsd())
                .verifyErrorSatisfies(ExchangeRateApiAdapterTest::esProveedorCaido);

        assertThat(logs.list).singleElement().satisfies(evento ->
                assertThat(evento.getFormattedMessage()).contains("TimeoutException"));
    }

    @Test
    void elLogDeUnaFallaNoLlevaNiElCuerpoNiLaUrl() {
        status = 500;
        respuesta = "{\"error\":\"detalle interno del proveedor\"}";

        StepVerifier.create(adapter().latestFromUsd()).expectError().verify();

        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(evento -> {
            assertThat(evento.getFormattedMessage()).doesNotContain("detalle interno", RUTA, "localhost");
            assertThat(evento.getThrowableProxy()).isNull();
        });
    }

    private static void esProveedorCaido(Throwable error) {
        assertThat(error).isInstanceOf(BadRequestException.class);
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(bre.getErrorResponse().getErrors()).singleElement().satisfies(detalle -> {
            assertThat(detalle.getCode()).isEqualTo(ErrorCodes.EXTERNAL_SERVICE_ERROR.getCode());
            assertThat(detalle.getDescription()).doesNotContain("detalle interno");
        });
    }
}
