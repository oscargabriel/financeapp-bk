package com.oscargabriel.financeapp.core.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.port.in.RejectPendingGroupPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class RejectPendingGroupUseCase implements RejectPendingGroupPort {

    private final TransactionRepositoryPort repositorio;

    /** Descartar es borrar, como rechazar uno: un pendiente no movio saldos (FIN-58). */
    @Override
    public Mono<Void> reject(UUID userId, UUID groupId) {
        return GrupoDelUsuario.pendientes(repositorio, groupId, userId)
                .flatMap(pendientes -> repositorio.deletePendingIn(
                        pendientes.stream().map(Transaction::id).toList(), userId))
                .flatMap(borrados -> borrados > 0 ? Mono.<Void>empty() : Mono.error(GrupoDelUsuario.noEncontrado()));
    }
}
