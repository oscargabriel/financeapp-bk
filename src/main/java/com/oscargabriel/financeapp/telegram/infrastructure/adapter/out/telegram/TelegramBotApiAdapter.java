package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.telegram;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.telegram.domain.model.ArchivoDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;
import com.oscargabriel.financeapp.telegram.domain.model.TecladoDelBot;
import com.oscargabriel.financeapp.telegram.domain.port.out.BotDeTelegramPort;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * La Bot API de Telegram (FIN-89): sendMessage, y desde FIN-61 getFile y la descarga del archivo,
 * answerCallbackQuery y editMessageReplyMarkup. La URL base sale de configuracion, nunca de la peticion.
 *
 * El token va en la ruta, y WebClientResponseException y los errores de conexion de Reactor Netty
 * llevan la URL en su mensaje: el log de una falla dice solo el metodo y el status o el tipo de la
 * excepcion, y el error que se propaga no lleva la causa, para que nadie mas arriba la loguee.
 *
 * WebClient.builder() y no el bean, por lo mismo que GeminiAssistantAdapter. Su tope en memoria es el del
 * archivo: una descarga mas grande falla en vez de llenar la memoria.
 */
@Component
@ConditionalOnProperty(name = "telegram.proveedor", havingValue = "telegram")
@Slf4j
public class TelegramBotApiAdapter implements BotDeTelegramPort {

    private static final String NO_ENVIADO = "Telegram no recibio el mensaje del bot";

    private static final String NO_DESCARGADO = "Telegram no entrego el archivo";

    /**
     * El file_path lo devuelve Telegram y va pegado a la ruta: solo caracteres de ruta y nada de .., para que
     * no salga de /file/bot<token>/ (design.md de FIN-61, decision 7).
     */
    private static final Pattern RUTA_DE_ARCHIVO = Pattern.compile("^[A-Za-z0-9_\\-/.]+$");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** request_contact comparte el contacto propio del que toca el boton, y solo funciona en chats privados. */
    private static final Map<String, Object> PEDIR_CONTACTO = Map.of(
            "keyboard", List.of(List.of(Map.of("text", "Compartir mi número", "request_contact", true))),
            "one_time_keyboard", true,
            "resize_keyboard", true);

    private static final Map<String, Object> QUITAR = Map.of("remove_keyboard", true);

    private static final Map<String, Object> SIN_BOTONES = Map.of("inline_keyboard", List.of());

    private final WebClient webClient;
    private final String token;
    private final Duration timeout;

    public TelegramBotApiAdapter(
            @Value("${telegram.base-url}") String baseUrl,
            @Value("${telegram.bot-token}") String token,
            @Value("${telegram.timeout}") Duration timeout) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(ArchivoDeTelegram.TOPE_ARCHIVO))
                .build();
        this.token = token;
        this.timeout = timeout;
    }

    @Override
    public Mono<Void> enviar(long chatId, RespuestaDelBot respuesta) {
        return llamar("sendMessage", cuerpo(chatId, respuesta), NO_ENVIADO);
    }

    @Override
    public Mono<byte[]> descargar(String fileId) {
        return Mono.defer(() -> webClient.post()
                        .uri("/bot{token}/getFile", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("file_id", fileId))
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(timeout)
                        .map(TelegramBotApiAdapter::rutaDeArchivo))
                .onErrorMap(fallo("getFile", NO_DESCARGADO))
                .flatMap(ruta -> webClient.get()
                        .uri("/file/bot{token}/" + ruta, token)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .timeout(timeout)
                        .onErrorMap(fallo("la descarga del archivo", NO_DESCARGADO)));
    }

    @Override
    public Mono<Void> cerrarEspera(String callbackId) {
        return llamar("answerCallbackQuery", Map.of("callback_query_id", callbackId), NO_ENVIADO);
    }

    @Override
    public Mono<Void> quitarBotones(long chatId, long messageId) {
        return llamar("editMessageReplyMarkup",
                Map.of("chat_id", chatId, "message_id", messageId, "reply_markup", SIN_BOTONES), NO_ENVIADO);
    }

    private Mono<Void> llamar(String metodo, Map<String, Object> cuerpo, String descripcion) {
        return Mono.defer(() -> webClient.post()
                        .uri("/bot{token}/" + metodo, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(cuerpo)
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(timeout))
                .then()
                .onErrorMap(fallo(metodo, descripcion));
    }

    /** reply_markup segun el teclado (FIN-60, FIN-61); sin el campo, Telegram deja el teclado que hubiera. */
    private static Map<String, Object> cuerpo(long chatId, RespuestaDelBot respuesta) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("chat_id", chatId);
        cuerpo.put("text", respuesta.texto());
        switch (respuesta.teclado()) {
            case TecladoDelBot.PedirContacto p -> cuerpo.put("reply_markup", PEDIR_CONTACTO);
            case TecladoDelBot.Quitar q -> cuerpo.put("reply_markup", QUITAR);
            case TecladoDelBot.Ninguno n -> { }
            case TecladoDelBot.ConfirmarODescartar botones -> cuerpo.put("reply_markup", Map.of(
                    "inline_keyboard", List.of(List.of(
                            Map.of("text", "Confirmar", "callback_data", botones.datosDeConfirmar()),
                            Map.of("text", "Descartar", "callback_data", botones.datosDeDescartar())))));
        }
        return cuerpo;
    }

    private static String rutaDeArchivo(String respuesta) {
        String ruta = JSON.readTree(respuesta).path("result").path("file_path").asString("");
        if (!RUTA_DE_ARCHIVO.matcher(ruta).matches() || ruta.contains("..")) {
            log.warn("Telegram devolvio en getFile una ruta de archivo que no es valida");
            throw noDisponible(NO_DESCARGADO);
        }
        return ruta;
    }

    private static Function<Throwable, Throwable> fallo(String metodo, String descripcion) {
        return e -> {
            if (e instanceof BadRequestException yaLogueado) {
                return yaLogueado;
            }
            if (e instanceof WebClientResponseException respuesta) {
                log.warn("Telegram rechazo {} con status={}", metodo, respuesta.getStatusCode().value());
            } else {
                log.warn("Telegram no respondio a {}: {}", metodo, e.getClass().getSimpleName());
            }
            return noDisponible(descripcion);
        };
    }

    private static BadRequestException noDisponible(String descripcion) {
        return new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR, descripcion,
                "server");
    }
}
