package com.oscargabriel.financeapp.telegram.application.usecase;

import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.port.in.AtenderUpdatePort;
import com.oscargabriel.financeapp.telegram.domain.port.out.BotDeTelegramPort;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Hasta que exista la vinculacion del chat (FIN-60), todo chat esta sin vincular: recibe un texto
 * fijo y el mensaje no llega al asistente, porque desde aqui no hay camino hacia el (FIN-89).
 */
@Service
@AllArgsConstructor
public class AtenderUpdateUseCase implements AtenderUpdatePort {

    static final String SIN_VINCULAR = "Este chat todavía no está vinculado a una cuenta de FinanceApp.";

    private final BotDeTelegramPort bot;

    /** El fallo del envio ya quedo en el log del adapter, con su status. */
    @Override
    public Mono<Void> atender(MensajeDeTelegram mensaje) {
        return bot.enviar(mensaje.chatId(), SIN_VINCULAR)
                .onErrorResume(error -> Mono.empty());
    }
}
