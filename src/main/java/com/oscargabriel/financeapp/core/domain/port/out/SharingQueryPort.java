package com.oscargabriel.financeapp.core.domain.port.out;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Sharing;

import reactor.core.publisher.Mono;

public interface SharingQueryPort {

    /** Lo compartido de esos movimientos, solo de los que son del usuario; los que no tienen nada no salen. */
    Mono<Map<UUID, Sharing>> findFor(UUID userId, List<UUID> transactionIds);
}
