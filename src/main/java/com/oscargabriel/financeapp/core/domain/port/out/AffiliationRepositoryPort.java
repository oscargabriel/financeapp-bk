package com.oscargabriel.financeapp.core.domain.port.out;

import java.util.Map;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Affiliation;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AffiliationRepositoryPort {

    /** Inserta la solicitud. Si otra abierta entre las mismas personas o al mismo correo entra antes, emite el 409. */
    Mono<Void> save(Affiliation affiliation);

    /**
     * Si el usuario ya tiene con esa persona una relacion abierta que ve: una enviada a ese correo que
     * sigue REQUESTED y, si addresseeId no es null, una abierta con ese usuario en cualquier sentido.
     */
    Mono<Boolean> existsOpenVisible(UUID userId, String email, UUID addresseeId);

    /** Las REQUESTED y ACCEPTED en las que participa, de la mas reciente a la mas antigua. */
    Flux<AffiliationView> findOpenByUser(UUID userId);

    /** Vacio si no existe o el usuario no es su remitente ni su destinatario. */
    Mono<Affiliation> findForParticipant(UUID id, UUID userId);

    /** La misma relacion, vista por ese participante, en cualquier estado. */
    Mono<AffiliationView> findViewForParticipant(UUID id, UUID userId);

    /** Cambia el estado solo si sigue en from; false si otra peticion ya lo cambio. */
    Mono<Boolean> changeStatus(UUID id, AffiliationStatus from, AffiliationStatus to);

    /** Las ACCEPTED en las que participa el usuario: el id de cada una con el de la otra persona (FIN-66). */
    Mono<Map<UUID, UUID>> findAcceptedByUser(UUID userId);
}
