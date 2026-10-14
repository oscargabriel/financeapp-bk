package com.oscargabriel.financeapp.core.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.port.in.ListAffiliationsPort;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliationRepositoryPort;

import reactor.core.publisher.Flux;

@Service
@AllArgsConstructor
public class ListAffiliationsUseCase implements ListAffiliationsPort {

    private final AffiliationRepositoryPort afiliaciones;

    @Override
    public Flux<AffiliationView> list(UUID userId) {
        return Flux.defer(() -> afiliaciones.findOpenByUser(userId));
    }
}
