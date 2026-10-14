package com.oscargabriel.financeapp.puente;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.core.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.ia.domain.model.NewMovement;
import com.oscargabriel.financeapp.ia.domain.model.RegisteredGroup;
import com.oscargabriel.financeapp.ia.domain.model.RegisteredMovement;
import com.oscargabriel.financeapp.ia.domain.port.out.PendingMovementPort;

import reactor.core.publisher.Mono;

/** Puente ia → core: los movimientos del asistente entran por el alta en lote del core (FA-148). */
@Component
@AllArgsConstructor
public class PendienteDelAsistenteAdapter implements PendingMovementPort {

    private final CreateTransactionsPort alta;

    /** Un lote con todo el mensaje, pendiente hasta que el usuario lo confirme: el alta le da un solo grupo (FIN-58). */
    @Override
    public Mono<RegisteredGroup> register(UUID userId, List<NewMovement> movements) {
        return alta.create(userId, TransactionOrigin.TELEGRAM, TransactionStatus.PENDING,
                        movements.stream().map(PendienteDelAsistenteAdapter::comando).toList())
                .collectList()
                .map(guardados -> new RegisteredGroup(guardados.getFirst().groupId(), guardados.stream()
                        .map(t -> new RegisteredMovement(t.id(), t.status().name(), t.occurredAt()))
                        .toList()));
    }

    private static CreateTransactionCommand comando(NewMovement movement) {
        return new CreateTransactionCommand(movement.type().name(), movement.accountId().toString(),
                texto(movement.destinationAccountId()), texto(movement.categoryId()), movement.amount(), null,
                movement.currencyCode(), movement.description(), null, movement.occurredAt());
    }

    private static String texto(UUID id) {
        return id == null ? null : id.toString();
    }
}
