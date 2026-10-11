package com.oscargabriel.financeapp.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Recurrence;
import com.oscargabriel.financeapp.domain.model.GroupScope;
import com.oscargabriel.financeapp.domain.model.RecurrenceTemplateChange;
import com.oscargabriel.financeapp.domain.model.RecurrenceView;
import com.oscargabriel.financeapp.domain.model.Transaction;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RecurrenceRepositoryPort {

    /** Inserta la serie y sus ocurrencias en una transaccion: o entra todo o nada. */
    Mono<Void> save(Recurrence serie, List<Transaction> ocurrencias);

    /**
     * Las activas del usuario con ocurrencias por venir (toda sin fin, y una con fin mientras le quede
     * alguna posterior a ahora), cada una con su proxima ocurrencia existente o null.
     */
    Flux<RecurrenceView> findActiveByUser(UUID userId);

    /** Vacio si no existe, es de otro usuario o esta cancelada. */
    Mono<Recurrence> findActiveByIdAndUser(UUID id, UUID userId);

    /** Las activas sin fin del usuario: las que la puesta al dia revisa. */
    Flux<Recurrence> findOpenActiveByUser(UUID userId);

    /** La ocurrencia existente mas proxima con fecha posterior a ahora; vacio si no hay. */
    Mono<Instant> findNextOccurrence(UUID recurrenceId);

    /**
     * Inserta las ocurrencias y deja el contador en serie.generatedCount(), solo si en la base sigue
     * valiendo esperado. false si otra peticion ya la puso al dia: entonces no inserta nada.
     */
    Mono<Boolean> append(Recurrence serie, int esperado, List<Transaction> ocurrencias);

    /**
     * Guarda la serie y aplica cambios a sus ocurrencias del alcance: con FUTURE, las de fecha posterior
     * a ahora; con ALL, todas. Si rehechas no es null, antes borra las futuras e inserta esas. Todo en
     * una transaccion; false si la serie ya no estaba activa.
     */
    Mono<Boolean> update(Recurrence serie, GroupScope alcance, Instant ahora, RecurrenceTemplateChange cambios,
            List<Transaction> rehechas);

    /** Borra las ocurrencias posteriores a ahora y la marca cancelada; false si no habia una activa. */
    Mono<Boolean> cancel(UUID id, UUID userId, Instant ahora);
}
