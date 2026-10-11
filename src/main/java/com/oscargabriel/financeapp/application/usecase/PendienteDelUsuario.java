package com.oscargabriel.financeapp.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;

/** La lectura que aprobar y rechazar comparten antes de escribir: el 404 y el 409. */
final class PendienteDelUsuario {

    private PendienteDelUsuario() {
    }

    /** El movimiento si es un pendiente del usuario; si no, el error que corresponde. */
    static Mono<Transaction> buscar(TransactionRepositoryPort repositorio, UUID transactionId, UUID userId) {
        return repositorio.findByIdAndUser(transactionId, userId)
                .switchIfEmpty(Mono.error(UpdateTransactionUseCase::noEncontrado))
                .flatMap(t -> t.status() == TransactionStatus.PENDING
                        ? Mono.just(t)
                        : Mono.error(yaConfirmado()));
    }

    /** La escritura condicionada no afecto filas: otro request lo aprobo o lo borro en medio. */
    static Mono<Void> exigirEscritura(boolean escrito) {
        return escrito ? Mono.empty() : Mono.error(UpdateTransactionUseCase.noEncontrado());
    }

    private static BadRequestException yaConfirmado() {
        return new BadRequestException(HttpStatus.CONFLICT, List.of(ErrorDetail.of(
                ErrorCodes.INVALID_STATE.getCode(), "El movimiento ya esta confirmado", "status")));
    }
}
