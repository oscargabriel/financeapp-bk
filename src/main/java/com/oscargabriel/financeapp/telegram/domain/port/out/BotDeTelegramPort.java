package com.oscargabriel.financeapp.telegram.domain.port.out;

import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;

import reactor.core.publisher.Mono;

public interface BotDeTelegramPort {

    /** Error si Telegram no acepta el mensaje o no responde; el log no lleva el token. */
    Mono<Void> enviar(long chatId, RespuestaDelBot respuesta);
}
