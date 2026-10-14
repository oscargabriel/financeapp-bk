package com.oscargabriel.financeapp.core.application.usecase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.Sharing;
import com.oscargabriel.financeapp.core.domain.port.in.DescribeSharingPort;
import com.oscargabriel.financeapp.core.domain.port.out.SharingQueryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class DescribeSharingUseCase implements DescribeSharingPort {

    private final SharingQueryPort consultas;

    @Override
    public Mono<Map<UUID, Sharing>> describe(UUID userId, List<UUID> transactionIds) {
        return transactionIds.isEmpty() ? Mono.just(Map.of()) : consultas.findFor(userId, transactionIds);
    }
}
