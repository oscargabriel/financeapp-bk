package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Sharing;

import reactor.core.publisher.Mono;

public interface DescribeSharingPort {

    /** Lo compartido de esos movimientos del usuario (FIN-66); los que no tienen nada compartido no salen. */
    Mono<Map<UUID, Sharing>> describe(UUID userId, List<UUID> transactionIds);
}
