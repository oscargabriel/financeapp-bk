package com.oscargabriel.financeapp.telegram.domain.port.out;

import reactor.core.publisher.Mono;

/** Los update_id que el bot ya atendio, para no procesar dos veces un reenvio de Telegram (FIN-61). */
public interface UpdatesAtendidosPort {

    /**
     * Anota el update y dice si es la primera vez que llega en los dos ultimos dias. Se llama antes de
     * procesarlo: como mucho una vez, aunque el proceso falle a mitad.
     */
    Mono<Boolean> primeraVez(long updateId);
}
