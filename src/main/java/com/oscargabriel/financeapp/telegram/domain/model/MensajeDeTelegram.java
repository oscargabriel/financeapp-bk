package com.oscargabriel.financeapp.telegram.domain.model;

/** Un mensaje que llego al bot. Por ahora solo importa a que chat responder (FIN-89). */
public record MensajeDeTelegram(long chatId) {
}
