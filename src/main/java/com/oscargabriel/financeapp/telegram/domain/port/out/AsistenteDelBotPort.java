package com.oscargabriel.financeapp.telegram.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelAsistente;

import reactor.core.publisher.Mono;

/** El asistente, visto desde el bot (FIN-61): lo cumple un adapter de puente/. */
public interface AsistenteDelBotPort {

    /**
     * Manda al asistente, como el usuario del chat y por el canal de Telegram, el texto y el archivo del mensaje.
     * {@code mime} y {@code archivo} van null sin archivo; {@code texto}, null sin texto.
     */
    Mono<RespuestaDelAsistente> atender(UUID usuario, String texto, String mime, byte[] archivo);
}
