package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.port.in.ApprovePendingTransactionPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class ApprovePendingTransactionUseCase implements ApprovePendingTransactionPort {

    private final TransactionRepositoryPort repositorio;
    private final AccountRepositoryPort cuentas;
    private final CategoryRepositoryPort categorias;
    private final Clock clock;

    /**
     * Los saldos los mueve el trigger al ver el paso de PENDING a CONFIRMED (design.md de FA-76). Antes de confirmar
     * se exige lo mismo que al confirmar un grupo (AprobacionDePendientes): cuentas activas, categoria viva y saldo.
     */
    @Override
    public Mono<Transaction> approve(UUID userId, UUID transactionId) {
        return PendienteDelUsuario.buscar(repositorio, transactionId, userId)
                .flatMap(pendiente -> AprobacionDePendientes.de(List.of(pendiente), userId, cuentas, categorias,
                                clock.instant())
                        .doOnNext(aprobacion -> aprobacion.exigir(pendiente))
                        .then(Mono.defer(() -> repositorio.confirm(transactionId, userId)))
                        .flatMap(PendienteDelUsuario::exigirEscritura)
                        .thenReturn(AprobacionDePendientes.confirmado(pendiente)));
    }
}
