package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.InstallmentPurchaseView;
import com.oscargabriel.financeapp.domain.port.in.ListInstallmentPurchasesPort;
import com.oscargabriel.financeapp.domain.port.out.InstallmentPurchaseRepositoryPort;

import reactor.core.publisher.Flux;

@Service
@AllArgsConstructor
public class ListInstallmentPurchasesUseCase implements ListInstallmentPurchasesPort {

    private final InstallmentPurchaseRepositoryPort compras;

    /** El filtro de activas y el orden los resuelve la consulta: son las cuotas que existen hoy. */
    @Override
    public Flux<InstallmentPurchaseView> list(UUID userId) {
        return compras.findActiveByUser(userId);
    }
}