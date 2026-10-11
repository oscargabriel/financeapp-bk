package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.domain.model.InstallmentPreview;
import com.oscargabriel.financeapp.domain.port.in.PreviewInstallmentPurchasePort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class PreviewInstallmentPurchaseUseCase implements PreviewInstallmentPurchasePort {

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final UserRepositoryPort usuarios;
    private final Clock clock;

    /** Las mismas validaciones del alta y el mismo plan, sin escribir nada. */
    @Override
    public Mono<InstallmentPreview> preview(UUID userId, CreateInstallmentPurchaseCommand compra) {
        return PlanDeCompra.de(userId, compra, cuentas, categorias, usuarios, clock)
                .map(listo -> new InstallmentPreview(listo.tarjeta().id(), compra.amount(),
                        listo.tarjeta().currencyCode(), compra.purchaseDate(), compra.installmentCount(),
                        PlanDeCompra.tasa(listo.tarjeta()), listo.cuotas(null)));
    }
}