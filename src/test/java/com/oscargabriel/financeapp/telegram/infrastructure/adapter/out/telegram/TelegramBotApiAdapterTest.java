package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.oscargabriel.financeapp.telegram.domain.model.ArchivoDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;
import com.oscargabriel.financeapp.telegram.domain.model.TecladoDelBot;
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

    /** Lo que responde cada ruta que no es la de por defecto, y las rutas en el orden en que se pidieron. */
    private final Map<String, byte[]> porRuta = new ConcurrentHashMap<>();
    private final List<String> rutas = new CopyOnWriteArrayList<>();

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void levantarBotApiFalsa() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getPath());
            rutas.add(intercambio.getRequestURI().getPath());
            byte[] propio = porRuta.get(intercambio.getRequestURI().getPath());
            if (propio == null) {
                cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] bytes = propio != null ? propio : respuesta.getBytes(StandardCharsets.UTF_8);
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

    private static RespuestaDelBot respuesta(String texto, TecladoDelBot teclado) {
        return new RespuestaDelBot(texto, teclado);
    }

    private JsonNode enviado() {
        return JsonMapper.builder().build().readTree(cuerpo.get());
    }

    @Test
    void enviaElTextoAlChatPorSendMessageConElTokenYSinTeclado() {
        StepVerifier.create(adapter().enviar(TelegramMother.CHAT, respuesta("Hola, ¿qué tal?", TecladoDelBot.NINGUNO)))
                .verifyComplete();

        assertThat(metodo.get()).isEqualTo("POST");
        assertThat(ruta.get()).isEqualTo("/bot" + TelegramMother.TOKEN + "/sendMessage");
        assertThat(enviado().path("chat_id").asLong()).isEqualTo(TelegramMother.CHAT);
        assertThat(enviado().path("text").asString()).isEqualTo("Hola, ¿qué tal?");
        assertThat(enviado().has("reply_markup")).isFalse();
    }

    /** request_contact solo manda el contacto propio del que toca el boton (FIN-60). */
    @Test
    void pedirElContactoMandaUnBotonQueLoComparte() {
        StepVerifier.create(adapter().enviar(TelegramMother.CHAT, respuesta("vincula", TecladoDelBot.PEDIR_CONTACTO)))
                .verifyComplete();

        JsonNode teclado = enviado().path("reply_markup");
        assertThat(teclado.path("one_time_keyboard").asBoolean()).isTrue();
        assertThat(teclado.path("resize_keyboard").asBoolean()).isTrue();
        JsonNode boton = teclado.path("keyboard").path(0).path(0);
        assertThat(boton.path("text").asString()).isEqualTo("Compartir mi número");
        assertThat(boton.path("request_contact").asBoolean()).isTrue();
        assertThat(teclado.path("keyboard")).hasSize(1);
        assertThat(teclado.path("keyboard").path(0)).hasSize(1);
    }

    @Test
    void quitarElTecladoMandaRemoveKeyboard() {
        StepVerifier.create(adapter().enviar(TelegramMother.CHAT, respuesta("listo", TecladoDelBot.QUITAR)))
                .verifyComplete();

        assertThat(enviado().path("reply_markup").path("remove_keyboard").asBoolean()).isTrue();
        assertThat(enviado().path("reply_markup").has("keyboard")).isFalse();
    }

    @Test
    void unRechazoDeTelegramEsUnFalloQueQuedaEnElLogConSuStatusYSinElToken() {
        status = 403;
        respuesta = "{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot was blocked by the user\"}";

        StepVerifier.create(adapter().enviar(TelegramMother.CHAT, respuesta("hola", TecladoDelBot.NINGUNO)))
                .verifyError();

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

        StepVerifier.create(sinServidor.enviar(TelegramMother.CHAT, respuesta("hola", TecladoDelBot.NINGUNO)))
                .verifyErrorSatisfies(error -> assertThat(error.getMessage()).doesNotContain(TelegramMother.TOKEN));

        assertThat(logs.list).singleElement().satisfies(evento -> {
            assertThat(evento.getFormattedMessage()).doesNotContain(TelegramMother.TOKEN, "/bot");
            assertThat(evento.getThrowableProxy()).isNull();
        });
    }

    private String ruta(String metodoDeLaApi) {
        return "/bot" + TelegramMother.TOKEN + "/" + metodoDeLaApi;
    }

    private static String rutaDeArchivo(String filePath) {
        return "/file/bot" + TelegramMother.TOKEN + "/" + filePath;
    }

    private void getFileDevuelve(String filePath) {
        respuesta = "{\"ok\":true,\"result\":{\"file_id\":\"voz\",\"file_path\":\"" + filePath + "\"}}";
    }

    private void logsSinElToken() {
        assertThat(logs.list).isNotEmpty().allSatisfy(evento -> {
            assertThat(evento.getFormattedMessage()).doesNotContain(TelegramMother.TOKEN, "/bot");
            assertThat(evento.getThrowableProxy()).isNull();
        });
    }

    /** FIN-61: los dos botones actuan sobre el grupo, que va en sus datos. */
    @Test
    void confirmarODescartarMandaDosBotonesInlineConElGrupo() {
        UUID grupo = UUID.fromString("71000000-0000-7000-8000-000000000009");

        StepVerifier.create(adapter().enviar(TelegramMother.CHAT,
                        respuesta("registre", new TecladoDelBot.ConfirmarODescartar(grupo))))
                .verifyComplete();

        JsonNode filas = enviado().path("reply_markup").path("inline_keyboard");
        assertThat(filas).hasSize(1);
        assertThat(filas.path(0)).hasSize(2);
        assertThat(filas.path(0).path(0).path("text").asString()).isEqualTo("Confirmar");
        assertThat(filas.path(0).path(0).path("callback_data").asString()).isEqualTo("confirmar:" + grupo);
        assertThat(filas.path(0).path(1).path("text").asString()).isEqualTo("Descartar");
        assertThat(filas.path(0).path(1).path("callback_data").asString()).isEqualTo("descartar:" + grupo);
    }

    @Test
    void descargaElArchivoPorGetFileYLaRutaQueDevuelveTelegram() {
        getFileDevuelve("voice/file_1.oga");
        porRuta.put(rutaDeArchivo("voice/file_1.oga"), new byte[] {'O', 'g', 'g', 'S', 1});

        StepVerifier.create(adapter().descargar("voz"))
                .assertNext(bytes -> assertThat(bytes).containsExactly('O', 'g', 'g', 'S', 1))
                .verifyComplete();

        assertThat(rutas).containsExactly(ruta("getFile"), rutaDeArchivo("voice/file_1.oga"));
        assertThat(enviado().path("file_id").asString()).isEqualTo("voz");
    }

    /** La ruta la devuelve Telegram: una con .. podria salir de /file/bot<token>/. */
    @Test
    void unFilePathConPuntosEsUnFalloSinDescargar() {
        getFileDevuelve("voice/../../otro/file.oga");

        StepVerifier.create(adapter().descargar("voz")).verifyError();

        assertThat(rutas).containsExactly(ruta("getFile"));
        logsSinElToken();
    }

    @Test
    void unFilePathConCaracteresRarosEsUnFalloSinDescargar() {
        getFileDevuelve("voice/file 1?.oga");

        StepVerifier.create(adapter().descargar("voz")).verifyError();

        assertThat(rutas).containsExactly(ruta("getFile"));
    }

    @Test
    void unaRespuestaDeGetFileSinRutaEsUnFallo() {
        respuesta = "{\"ok\":true,\"result\":{\"file_id\":\"voz\"}}";

        StepVerifier.create(adapter().descargar("voz")).verifyError();

        assertThat(rutas).containsExactly(ruta("getFile"));
    }

    @Test
    void unArchivoPorEncimaDelTopeEsUnFallo() {
        getFileDevuelve("photos/file_2.jpg");
        porRuta.put(rutaDeArchivo("photos/file_2.jpg"), new byte[ArchivoDeTelegram.TOPE_ARCHIVO + 1]);

        StepVerifier.create(adapter().descargar("foto")).verifyError();

        logsSinElToken();
    }

    @Test
    void unRechazoDeGetFileEsUnFalloConSuStatusYSinElToken() {
        status = 400;
        respuesta = "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: invalid file_id\"}";

        StepVerifier.create(adapter().descargar("voz"))
                .verifyErrorSatisfies(error -> assertThat(error.getMessage()).doesNotContain(TelegramMother.TOKEN));

        assertThat(logs.list).singleElement().satisfies(evento ->
                assertThat(evento.getFormattedMessage()).contains("getFile", "400"));
        logsSinElToken();
    }

    @Test
    void cerrarLaEsperaLlamaAnswerCallbackQueryConElId() {
        StepVerifier.create(adapter().cerrarEspera("cb-1")).verifyComplete();

        assertThat(ruta.get()).isEqualTo(ruta("answerCallbackQuery"));
        assertThat(enviado().path("callback_query_id").asString()).isEqualTo("cb-1");
    }

    @Test
    void quitarLosBotonesMandaUnTecladoInlineVacioAlMensaje() {
        StepVerifier.create(adapter().quitarBotones(TelegramMother.CHAT, 50L)).verifyComplete();

        assertThat(ruta.get()).isEqualTo(ruta("editMessageReplyMarkup"));
        assertThat(enviado().path("chat_id").asLong()).isEqualTo(TelegramMother.CHAT);
        assertThat(enviado().path("message_id").asLong()).isEqualTo(50L);
        assertThat(enviado().path("reply_markup").path("inline_keyboard").isArray()).isTrue();
        assertThat(enviado().path("reply_markup").path("inline_keyboard")).isEmpty();
    }

    @Test
    void unRechazoAlQuitarLosBotonesEsUnFalloConElMetodoYSinElToken() {
        status = 400;
        respuesta = "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: message is not modified\"}";

        StepVerifier.create(adapter().quitarBotones(TelegramMother.CHAT, 50L)).verifyError();

        assertThat(logs.list).singleElement().satisfies(evento ->
                assertThat(evento.getFormattedMessage()).contains("editMessageReplyMarkup", "400"));
        logsSinElToken();
    }

    @Test
    void unRechazoAlCerrarLaEsperaEsUnFalloConElMetodo() {
        status = 400;
        respuesta = "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: query is too old\"}";

        StepVerifier.create(adapter().cerrarEspera("cb-1")).verifyError();

        assertThat(logs.list).singleElement().satisfies(evento ->
                assertThat(evento.getFormattedMessage()).contains("answerCallbackQuery", "400"));
        logsSinElToken();
    }
}
