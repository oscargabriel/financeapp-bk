package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.port.in.ApprovePendingGroupPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class ApprovePendingGroupUseCase implements ApprovePendingGroupPort {

    private final TransactionRepositoryPort repositorio;
    private final AccountRepositoryPort cuentas;
    private final CategoryRepositoryPort categorias;
    private final Clock clock;

    /**
     * Todo o nada (FIN-58): se validan juntos y se confirman en una sola transaccion. Lo aprobado o rechazado uno a
     * uno ya no esta entre los pendientes y no se toca.
     */
    @Override
    public Flux<Transaction> approve(UUID userId, UUID groupId) {
        return GrupoDelUsuario.pendientes(repositorio, groupId, userId)
                .flatMapMany(pendientes -> AprobacionDePendientes.de(pendientes, userId, cuentas, categorias,
                                clock.instant())
                        .doOnNext(aprobacion -> aprobacion.exigirGrupo(pendientes))
                        .then(Mono.defer(() -> repositorio.confirmAll(
                                pendientes.stream().map(Transaction::id).toList(), userId)))
                        .flatMap(escrito -> escrito ? Mono.empty() : Mono.error(GrupoDelUsuario.noEncontrado()))
                        .thenMany(Flux.fromIterable(pendientes).map(AprobacionDePendientes::confirmado)));
    }
}
