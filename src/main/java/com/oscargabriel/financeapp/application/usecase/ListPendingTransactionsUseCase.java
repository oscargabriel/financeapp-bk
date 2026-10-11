package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.port.in.ListPendingTransactionsPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Flux;

@Service
@AllArgsConstructor
public class ListPendingTransactionsUseCase implements ListPendingTransactionsPort {

    private final TransactionRepositoryPort repositorio;

    @Override
    public Flux<Transaction> listPending(UUID userId) {
        return repositorio.findPendingByUser(userId);
    }
}
