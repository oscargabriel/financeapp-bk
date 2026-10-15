package com.oscargabriel.financeapp.telegram.domain.port.in;

import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;

import reactor.core.publisher.Mono;

public interface AtenderUpdatePort {

    /** Nunca termina en error: un fallo al responder no puede hacer que Telegram reintente el update. */
    Mono<Void> atender(MensajeDeTelegram mensaje);
}
