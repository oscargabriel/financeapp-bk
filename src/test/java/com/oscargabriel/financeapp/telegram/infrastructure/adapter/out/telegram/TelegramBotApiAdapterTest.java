package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.telegram;

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

import com.oscargabriel.financeapp.telegram.support.TelegramMother;
import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TelegramBotApiAdapterTest {

    private HttpServer servidor;
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String respuesta = "{\"ok\":true,\"result\":{\"message_id\":7}}";

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void levantarBotApiFalsa() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getPath());
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(status, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(TelegramBotApiAdapter.class)).addAppender(logs);
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
        ((Logger) LoggerFactory.getLogger(TelegramBotApiAdapter.class)).detachAppender(logs);
    }

    /** Holgado: el primer request de la clase paga el arranque en frio de Netty. */
    private TelegramBotApiAdapter adapter() {
        return new TelegramBotApiAdapter("http://localhost:" + servidor.getAddress().getPort(),
                TelegramMother.TOKEN, Duration.ofSeconds(5));
    }

    @Test
    void enviaElTextoAlChatPorSendMessageConElToken() {
        StepVerifier.create(adapter().enviar(TelegramMother.CHAT, "Hola, ¿qué tal?"))
                .verifyComplete();

        assertThat(metodo.get()).isEqualTo("POST");
        assertThat(ruta.get()).isEqualTo("/bot" + TelegramMother.TOKEN + "/sendMessage");
        JsonNode enviado = JsonMapper.builder().build().readTree(cuerpo.get());
        assertThat(enviado.path("chat_id").asLong()).isEqualTo(TelegramMother.CHAT);
        assertThat(enviado.path("text").asString()).isEqualTo("Hola, ¿qué tal?");
    }

    @Test
    void unRechazoDeTelegramEsUnFalloQueQuedaEnElLogConSuStatusYSinElToken() {
        status = 403;
        respuesta = "{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot was blocked by the user\"}";

        StepVerifier.create(adapter().enviar(TelegramMother.CHAT, "hola")).verifyError();

        assertThat(logs.list).singleElement().satisfies(evento -> {
            assertThat(evento.getFormattedMessage()).contains("403").doesNotContain(TelegramMother.TOKEN, "/bot");
            assertThat(evento.getThrowableProxy()).isNull();
        });
    }

    /** El error de conexion de Reactor Netty lleva la URL, y con ella el token, en su mensaje. */
    @Test
    void sinRespuestaDeTelegramEsUnFalloQueNoDejaElTokenEnElLog() {
        TelegramBotApiAdapter sinServidor = adapter();
        servidor.stop(0);

        StepVerifier.create(sinServidor.enviar(TelegramMother.CHAT, "hola"))
                .verifyErrorSatisfies(error -> assertThat(error.getMessage()).doesNotContain(TelegramMother.TOKEN));

        assertThat(logs.list).singleElement().satisfies(evento -> {
            assertThat(evento.getFormattedMessage()).doesNotContain(TelegramMother.TOKEN, "/bot");
            assertThat(evento.getThrowableProxy()).isNull();
        });
    }
}
