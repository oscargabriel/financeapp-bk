package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.port.in.ListAccountsPort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;

import reactor.core.publisher.Flux;

@Service
@AllArgsConstructor
public class ListAccountsUseCase implements ListAccountsPort {

    private final AccountQueryPort query;

    @Override
    public Flux<Account> list(UUID userId, boolean includeInactive) {
        return Flux.defer(() -> query.findByUser(userId, includeInactive));
    }
}
