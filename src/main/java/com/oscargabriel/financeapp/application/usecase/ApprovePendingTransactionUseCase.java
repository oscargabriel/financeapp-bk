package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.domain.port.in.ApprovePendingTransactionPort;
import com.oscargabriel.financeapp.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class ApprovePendingTransactionUseCase implements ApprovePendingTransactionPort {

    private final TransactionRepositoryPort repositorio;
    private final AccountRepositoryPort cuentas;

    /** Los saldos los mueve el trigger al ver el paso de PENDING a CONFIRMED (design.md de FA-76). */
    @Override
    public Mono<Transaction> approve(UUID userId, UUID transactionId) {
        return PendienteDelUsuario.buscar(repositorio, transactionId, userId)
                .flatMap(pendiente -> exigirCuentasActivas(pendiente, userId)
                        .then(Mono.defer(() -> repositorio.confirm(transactionId, userId)))
                        .flatMap(PendienteDelUsuario::exigirEscritura)
                        .thenReturn(confirmado(pendiente)));
    }

    /**
     * El trigger no mira is_active: sin este chequeo, aprobar moveria el saldo de una cuenta que el alta
     * ya no acepta (FA-102). Una cuenta vacia solo seria una borrada, y esas ya no tienen pendientes.
     */
    private Mono<Void> exigirCuentasActivas(Transaction pendiente, UUID userId) {
        Mono<ErrorDetail> destino = pendiente.destinationAccountId() == null
                ? Mono.empty()
                : desactivada(pendiente.destinationAccountId(), userId, "destinationAccountId",
                        "La cuenta destino esta desactivada");
        return Flux.concat(desactivada(pendiente.accountId(), userId, "accountId", "La cuenta esta desactivada"),
                        destino)
                .collectList()
                .flatMap(errores -> errores.isEmpty()
                        ? Mono.empty()
                        : Mono.error(new BadRequestException(HttpStatus.CONFLICT, errores)));
    }

    private Mono<ErrorDetail> desactivada(UUID cuentaId, UUID userId, String campo, String descripcion) {
        return cuentas.findActiveByIdAndUser(cuentaId, userId)
                .filter(cuenta -> !cuenta.active())
                .map(cuenta -> ErrorDetail.of(ErrorCodes.INVALID_STATE.getCode(), descripcion, campo));
    }

    private static Transaction confirmado(Transaction t) {
        return new Transaction(t.id(), t.userId(), t.type(), t.accountId(), t.destinationAccountId(),
                t.categoryId(), t.amount(), t.currencyCode(), t.description(), t.notes(), t.occurredAt(),
                TransactionStatus.CONFIRMED, t.origin(), t.recurrenceId());
    }
}
