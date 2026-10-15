package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.telegram;

import java.time.Duration;
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
    public Mono<Void> enviar(long chatId, String texto) {
        return Mono.defer(() -> webClient.post()
                        .uri("/bot{token}/sendMessage", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("chat_id", chatId, "text", texto))
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(timeout))
                .then()
                .onErrorMap(TelegramBotApiAdapter::noEnviado);
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
