package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.telegram;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;
import com.oscargabriel.financeapp.telegram.domain.port.out.BotDeTelegramPort;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * El bot con el que bruno/telegram/ no habla con Telegram (design.md de FIN-89, decision 3): el
 * envio solo queda en el log, con su teclado. Con el perfil prod no existe aunque se pida, y entonces
 * no hay bot y no arranca.
 *
 * Desde FIN-61 entrega dos archivos fijos, con la cabecera justa para que el asistente los acepte:
 * stub-jpeg y stub-ogg. Cualquier otro file_id falla como si Telegram no lo entregara.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "telegram.proveedor", havingValue = "stub")
@Slf4j
public class StubTelegramBotAdapter implements BotDeTelegramPort {

    private static final Map<String, byte[]> ARCHIVOS = Map.of(
            "stub-jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'},
            "stub-ogg", new byte[] {'O', 'g', 'g', 'S', 0, 2, 0, 0, 0, 0});

    /** En un onNext y no en fromRunnable: ReactorMdcHook solo copia el requestId en las senales. */
    @Override
    public Mono<Void> enviar(long chatId, RespuestaDelBot respuesta) {
        return Mono.just(respuesta)
                .doOnNext(enviada -> log.info("Bot stub: al chat {} -> {} [teclado {}]",
                        chatId, enviada.texto(), enviada.teclado()))
                .then();
    }

    @Override
    public Mono<byte[]> descargar(String fileId) {
        return Mono.justOrEmpty(ARCHIVOS.get(fileId))
                .map(byte[]::clone)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.BAD_GATEWAY,
                        ErrorCodes.EXTERNAL_SERVICE_ERROR, "Telegram no entrego el archivo", "server")));
    }

    @Override
    public Mono<Void> cerrarEspera(String callbackId) {
        return Mono.just(callbackId)
                .doOnNext(id -> log.info("Bot stub: cierra la espera del boton {}", id))
                .then();
    }

    @Override
    public Mono<Void> quitarBotones(long chatId, long messageId) {
        return Mono.just(messageId)
                .doOnNext(mensaje -> log.info("Bot stub: quita los botones del mensaje {} del chat {}", mensaje, chatId))
                .then();
    }
}
