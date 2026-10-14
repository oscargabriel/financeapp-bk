package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.AffiliationView;

import reactor.core.publisher.Mono;

public interface SendAffiliationRequestPort {

    /** Responde igual tenga o no cuenta el correo: el remitente no sabe cuales estan registrados. */
    Mono<AffiliationView> send(UUID userId, String email);
}
