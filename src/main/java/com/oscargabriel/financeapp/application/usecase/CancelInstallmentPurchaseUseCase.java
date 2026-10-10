package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.port.in.CancelInstallmentPurchasePort;
import com.oscargabriel.financeapp.domain.port.out.InstallmentPurchaseRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class CancelInstallmentPurchaseUseCase implements CancelInstallmentPurchasePort {

    private final InstallmentPurchaseRepositoryPort compras;
    private final Clock clock;

    /** Una sola escritura filtrada por usuario y estado: cero filas es 404, sin una lectura previa. */
    @Override
    public Mono<Void> cancel(UUID userId, UUID purchaseId) {
        return Mono.defer(() -> compras.cancel(purchaseId, userId, clock.instant()))
                .flatMap(cancelada -> cancelada
                        ? Mono.<Void>empty()
                        : Mono.error(UpdateInstallmentPurchaseUseCase.noEncontrada()));
    }
}