package com.oscargabriel.financeapp.core.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.AffiliateBalance;
import com.oscargabriel.financeapp.core.domain.port.in.ListAffiliateBalancesPort;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliateBalanceQueryPort;

import reactor.core.publisher.Flux;

@Service
@AllArgsConstructor
public class ListAffiliateBalancesUseCase implements ListAffiliateBalancesPort {

    private final AffiliateBalanceQueryPort saldos;

    @Override
    public Flux<AffiliateBalance> list(UUID userId) {
        return Flux.defer(() -> saldos.findBalances(userId));
    }
}
