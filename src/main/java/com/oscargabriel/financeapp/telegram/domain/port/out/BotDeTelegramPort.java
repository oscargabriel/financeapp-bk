package com.oscargabriel.financeapp.telegram.domain.port.out;

import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;

import reactor.core.publisher.Mono;

/** La Bot API de Telegram. Todo error se loguea sin el token y sale sin la causa, que lleva la URL. */
public interface BotDeTelegramPort {

    /** Error si Telegram no acepta el mensaje o no responde. */
    Mono<Void> enviar(long chatId, RespuestaDelBot respuesta);

    /**
     * Los bytes del archivo, que no se guardan (FIN-61). Error si Telegram no lo entrega, si su ruta no es
     * valida o si pasa de ArchivoDeTelegram.TOPE_ARCHIVO.
     */
    Mono<byte[]> descargar(String fileId);

    /** Le dice al cliente que el toque del boton ya se atendio, para que deje de mostrar la espera. */
    Mono<Void> cerrarEspera(String callbackId);

    /** Quita los botones inline de un mensaje que ya envio el bot. */
    Mono<Void> quitarBotones(long chatId, long messageId);
}
