package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.persistence;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.telegram.domain.port.out.UpdatesAtendidosPort;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Los updates atendidos en finance.telegram_updates (design.md de FIN-61, decision 3). En la base y no en
 * memoria para sobrevivir a un reinicio y valer con mas de una instancia. Un error se loguea solo con su
 * tipo, como en TelegramChatR2dbcAdapter.
 */
@Component
@AllArgsConstructor
@Slf4j
public class TelegramUpdateR2dbcAdapter implements UpdatesAtendidosPort {

    /**
     * Telegram guarda los updates no entregados hasta 24 horas, y tras una semana sin updates elige el
     * siguiente update_id al azar: una fila vieja podria tapar un update nuevo.
     */
    private static final String OLVIDAR_VIEJOS = """
            DELETE FROM finance.telegram_updates
             WHERE received_at < now() - interval '2 days'
            """;

    /** Si dos instancias reciben el mismo update a la vez, solo una inserta: la clave primaria decide. */
    private static final String ANOTAR = """
            INSERT INTO finance.telegram_updates (update_id)
            VALUES (:updateId)
            ON CONFLICT (update_id) DO NOTHING
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Boolean> primeraVez(long updateId) {
        return databaseClient.sql(OLVIDAR_VIEJOS)
                .fetch()
                .rowsUpdated()
                .then(Mono.defer(() -> databaseClient.sql(ANOTAR)
                        .bind("updateId", updateId)
                        .fetch()
                        .rowsUpdated()))
                .map(insertadas -> insertadas == 1)
                .doOnError(e -> log.warn("No se pudo anotar el update {}: {}", updateId, e.getClass().getSimpleName()));
    }
}
