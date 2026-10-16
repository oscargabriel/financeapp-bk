package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;
import com.oscargabriel.financeapp.telegram.domain.port.out.BotDeTelegramPort;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * El bot con el que bruno/telegram/ no habla con Telegram (design.md de FIN-89, decision 3): el
 * envio solo queda en el log, con su teclado. Con el perfil prod no existe aunque se pida, y entonces
 * no hay bot y no arranca.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "telegram.proveedor", havingValue = "stub")
@Slf4j
public class StubTelegramBotAdapter implements BotDeTelegramPort {

    /** En un onNext y no en fromRunnable: ReactorMdcHook solo copia el requestId en las senales. */
    @Override
    public Mono<Void> enviar(long chatId, RespuestaDelBot respuesta) {
        return Mono.just(respuesta)
                .doOnNext(enviada -> log.info("Bot stub: al chat {} -> {} [teclado {}]",
                        chatId, enviada.texto(), enviada.teclado()))
                .then();
    }
}
