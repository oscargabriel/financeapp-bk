package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
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
    private final Clock clock;

    /**
     * Los saldos los mueve el trigger al ver el paso de PENDING a CONFIRMED (design.md de FA-76). Antes de confirmar,
     * las cuentas tienen que estar activas y, despues, quedar con saldo (FA-133): con una desactivada solo sale ese
     * error.
     */
    @Override
    public Mono<Transaction> approve(UUID userId, UUID transactionId) {
        return PendienteDelUsuario.buscar(repositorio, transactionId, userId)
                .flatMap(pendiente -> cuentasDe(pendiente, userId)
                        .doOnNext(suyas -> {
                            exigirCuentasActivas(pendiente, suyas);
                            new SaldoNoNegativo(suyas).exigir(
                                    SaldoNoNegativo.efecto(confirmado(pendiente), clock.instant()), "amount");
                        })
                        .then(Mono.defer(() -> repositorio.confirm(transactionId, userId)))
                        .flatMap(PendienteDelUsuario::exigirEscritura)
                        .thenReturn(confirmado(pendiente)));
    }

    /** Por el id con que las nombra el pendiente. Una cuenta ausente solo seria una borrada, y esas ya no tienen pendientes. */
    private Mono<Map<UUID, Account>> cuentasDe(Transaction pendiente, UUID userId) {
        return Flux.fromStream(Stream.of(pendiente.accountId(), pendiente.destinationAccountId()).filter(Objects::nonNull))
                .concatMap(id -> cuentas.findActiveByIdAndUser(id, userId).map(cuenta -> Map.entry(id, cuenta)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /**
     * El trigger no mira is_active: sin este chequeo, aprobar moveria el saldo de una cuenta que el alta
     * ya no acepta (FA-102).
     */
    private static void exigirCuentasActivas(Transaction pendiente, Map<UUID, Account> suyas) {
        List<ErrorDetail> errores = new ArrayList<>();
        desactivada(suyas.get(pendiente.accountId()), "accountId", "La cuenta esta desactivada")
                .ifPresent(errores::add);
        desactivada(suyas.get(pendiente.destinationAccountId()), "destinationAccountId",
                "La cuenta destino esta desactivada").ifPresent(errores::add);
        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.CONFLICT, errores);
        }
    }

    private static Optional<ErrorDetail> desactivada(Account cuenta, String campo, String descripcion) {
        return Optional.ofNullable(cuenta)
                .filter(c -> !c.active())
                .map(c -> ErrorDetail.of(ErrorCodes.INVALID_STATE.getCode(), descripcion, campo));
    }

    private static Transaction confirmado(Transaction t) {
        return new Transaction(t.id(), t.userId(), t.type(), t.accountId(), t.destinationAccountId(),
                t.categoryId(), t.amount(), t.currencyCode(), t.description(), t.notes(), t.occurredAt(),
                TransactionStatus.CONFIRMED, t.origin(), t.recurrenceId(), t.installment(), t.destinationAmount(), t.original());
    }
}
