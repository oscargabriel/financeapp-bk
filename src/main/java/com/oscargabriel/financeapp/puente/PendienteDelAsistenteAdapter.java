package com.oscargabriel.financeapp.puente;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.core.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.ia.domain.model.NewMovement;
import com.oscargabriel.financeapp.ia.domain.model.RegisteredMovement;
import com.oscargabriel.financeapp.ia.domain.port.out.PendingMovementPort;

import reactor.core.publisher.Mono;

/** Puente ia → core: el movimiento del asistente entra por el alta en lote del core (FA-148). */
@Component
@AllArgsConstructor
public class PendienteDelAsistenteAdapter implements PendingMovementPort {

    private final CreateTransactionsPort alta;

    /** Un lote de uno; el origen TELEGRAM es el que lo deja pendiente de aprobacion. */
    @Override
    public Mono<RegisteredMovement> register(UUID userId, NewMovement movement) {
        CreateTransactionCommand comando = new CreateTransactionCommand(movement.type().name(),
                movement.accountId().toString(), texto(movement.destinationAccountId()),
                texto(movement.categoryId()), movement.amount(), null, movement.currencyCode(),
                movement.description(), null, movement.occurredAt());
        return alta.create(userId, TransactionOrigin.TELEGRAM, List.of(comando))
                .next()
                .map(t -> new RegisteredMovement(t.id(), t.status().name(), t.occurredAt()));
    }

    private static String texto(UUID id) {
        return id == null ? null : id.toString();
    }
}
