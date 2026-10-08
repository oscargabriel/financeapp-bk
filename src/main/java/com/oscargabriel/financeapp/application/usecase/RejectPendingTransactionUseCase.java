package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.port.in.RejectPendingTransactionPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class RejectPendingTransactionUseCase implements RejectPendingTransactionPort {

    private final TransactionRepositoryPort repositorio;

    /** Rechazar es borrar: un pendiente no movio saldos, asi que no hay nada que revertir. */
    @Override
    public Mono<Void> reject(UUID userId, UUID transactionId) {
        return PendienteDelUsuario.buscar(repositorio, transactionId, userId)
                .flatMap(pendiente -> repositorio.deletePending(transactionId, userId))
                .flatMap(PendienteDelUsuario::exigirEscritura);
    }
}
