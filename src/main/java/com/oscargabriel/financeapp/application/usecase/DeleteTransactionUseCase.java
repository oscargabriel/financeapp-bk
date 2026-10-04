package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.port.in.DeleteTransactionPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class DeleteTransactionUseCase implements DeleteTransactionPort {

    private final TransactionRepositoryPort repositorio;

    /** Un solo DELETE filtrado por usuario: cero filas es 404, sin una lectura previa. */
    @Override
    public Mono<Void> delete(UUID userId, UUID transactionId) {
        return repositorio.deleteByIdAndUser(transactionId, userId)
                .flatMap(borrado -> borrado
                        ? Mono.<Void>empty()
                        : Mono.error(UpdateTransactionUseCase.noEncontrado()));
    }
}
