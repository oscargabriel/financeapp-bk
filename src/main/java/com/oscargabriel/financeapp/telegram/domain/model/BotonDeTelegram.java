package com.oscargabriel.financeapp.telegram.domain.model;

/**
 * El toque de un boton inline (FIN-61). {@code chatId} y {@code messageId} son nulos si Telegram ya no
 * entrega el mensaje del boton. {@code datos} es el callback_data tal como llega: un cliente modificado
 * puede mandar cualquier cosa.
 */
public record BotonDeTelegram(String callbackId, Long chatId, boolean privado, Long messageId, String datos)
        implements EventoDeTelegram {
}
