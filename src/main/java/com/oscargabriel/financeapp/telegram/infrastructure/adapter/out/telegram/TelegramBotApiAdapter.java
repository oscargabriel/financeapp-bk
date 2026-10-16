package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.telegram;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;
import com.oscargabriel.financeapp.telegram.domain.port.out.BotDeTelegramPort;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * sendMessage de la Bot API de Telegram (FIN-89). La URL base sale de configuracion, nunca de la
 * peticion.
 *
 * El token va en la ruta, y WebClientResponseException y los errores de conexion de Reactor Netty
 * llevan la URL en su mensaje: el log de una falla dice solo el status o el tipo de la excepcion, y
 * el error que se propaga no lleva la causa, para que nadie mas arriba la loguee.
 *
 * WebClient.builder() y no el bean, por lo mismo que GeminiAssistantAdapter.
 */
@Component
@ConditionalOnProperty(name = "telegram.proveedor", havingValue = "telegram")
@Slf4j
public class TelegramBotApiAdapter implements BotDeTelegramPort {

    private static final String NO_ENVIADO = "Telegram no recibio el mensaje del bot";

    /** request_contact comparte el contacto propio del que toca el boton, y solo funciona en chats privados. */
    private static final Map<String, Object> PEDIR_CONTACTO = Map.of(
            "keyboard", List.of(List.of(Map.of("text", "Compartir mi número", "request_contact", true))),
            "one_time_keyboard", true,
            "resize_keyboard", true);

    private static final Map<String, Object> QUITAR = Map.of("remove_keyboard", true);

    private final WebClient webClient;
    private final String token;
    private final Duration timeout;

    public TelegramBotApiAdapter(
            @Value("${telegram.base-url}") String baseUrl,
            @Value("${telegram.bot-token}") String token,
            @Value("${telegram.timeout}") Duration timeout) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.token = token;
        this.timeout = timeout;
    }

    @Override
    public Mono<Void> enviar(long chatId, RespuestaDelBot respuesta) {
        return Mono.defer(() -> webClient.post()
                        .uri("/bot{token}/sendMessage", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(cuerpo(chatId, respuesta))
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(timeout))
                .then()
                .onErrorMap(TelegramBotApiAdapter::noEnviado);
    }

    /** reply_markup segun el teclado (FIN-60); sin el campo, Telegram deja el teclado que hubiera. */
    private static Map<String, Object> cuerpo(long chatId, RespuestaDelBot respuesta) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("chat_id", chatId);
        cuerpo.put("text", respuesta.texto());
        switch (respuesta.teclado()) {
            case PEDIR_CONTACTO -> cuerpo.put("reply_markup", PEDIR_CONTACTO);
            case QUITAR -> cuerpo.put("reply_markup", QUITAR);
            case NINGUNO -> { }
        }
        return cuerpo;
    }

    private static BadRequestException noEnviado(Throwable e) {
        if (e instanceof WebClientResponseException respuesta) {
            log.warn("Telegram rechazo sendMessage con status={}", respuesta.getStatusCode().value());
        } else {
            log.warn("Telegram no respondio a sendMessage: {}", e.getClass().getSimpleName());
        }
        return new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR, NO_ENVIADO,
                "server");
    }
}
