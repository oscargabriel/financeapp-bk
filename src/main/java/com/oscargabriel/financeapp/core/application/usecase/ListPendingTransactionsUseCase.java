package com.oscargabriel.financeapp.core.application.usecase;

import java.util.Optional;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.port.in.ListPendingTransactionsPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Flux;

@Service
@AllArgsConstructor
public class ListPendingTransactionsUseCase implements ListPendingTransactionsPort {

    private final TransactionRepositoryPort repositorio;

    /** El correo se normaliza como en el registro, que es como se guarda (FIN-68). */
    @Override
    public Flux<Transaction> listPending(UUID userId, Optional<String> sharedBy) {
        return sharedBy
                .map(correo -> repositorio.findPendingSharedBy(userId, correo.trim().toLowerCase()))
                .orElseGet(() -> repositorio.findPendingByUser(userId));
    }
}
