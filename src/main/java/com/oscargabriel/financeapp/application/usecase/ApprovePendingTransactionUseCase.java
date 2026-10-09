package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.domain.port.in.ApprovePendingTransactionPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class ApprovePendingTransactionUseCase implements ApprovePendingTransactionPort {

    private final TransactionRepositoryPort repositorio;

    /** Los saldos los mueve el trigger al ver el paso de PENDING a CONFIRMED (design.md de FA-76). */
    @Override
    public Mono<Transaction> approve(UUID userId, UUID transactionId) {
        return PendienteDelUsuario.buscar(repositorio, transactionId, userId)
                .flatMap(pendiente -> repositorio.confirm(transactionId, userId)
                        .flatMap(PendienteDelUsuario::exigirEscritura)
                        .thenReturn(confirmado(pendiente)));
    }

    private static Transaction confirmado(Transaction t) {
        return new Transaction(t.id(), t.userId(), t.type(), t.accountId(), t.destinationAccountId(),
                t.categoryId(), t.amount(), t.currencyCode(), t.description(), t.notes(), t.occurredAt(),
                TransactionStatus.CONFIRMED, t.origin());
    }
}
