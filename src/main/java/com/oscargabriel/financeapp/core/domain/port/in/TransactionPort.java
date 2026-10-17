package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.ApprovalChoice;
import com.oscargabriel.financeapp.core.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.UpdateTransactionCommand;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransactionPort {

    /**
     * Todo o nada: o entran todos los movimientos, en el orden del lote, o no entra ninguno. Entran con el estado
     * que pide quien registra, salvo lo convertido de otra moneda, que queda pendiente igual. Los pendientes del
     * lote comparten un grupo nuevo (FIN-58).
     */
    Flux<Transaction> create(UUID userId, TransactionOrigin origen, TransactionStatus estado,
            List<CreateTransactionCommand> lote);

    /** Devuelve el movimiento como quedo. Inexistente o de otro usuario: el mismo 404. */
    Mono<Transaction> update(UUID userId, UUID transactionId, UpdateTransactionCommand parche);

    /** Borrado fisico. Inexistente o de otro usuario: el mismo 404. */
    Mono<Void> delete(UUID userId, UUID transactionId);

    /**
     * Los pendientes del usuario, del mas reciente al mas antiguo. Con sharedBy, solo los que compartio la persona de
     * ese correo (FIN-68). Ninguno: Flux vacio.
     */
    Flux<Transaction> pending(UUID userId, Optional<String> sharedBy);

    /**
     * Confirma un pendiente y devuelve el movimiento ya CONFIRMED. Inexistente o de otro usuario: 404;
     * ya confirmado: 409. Con la cuenta origen o destino desactivada: 409 sobre accountId o
     * destinationAccountId, uno por cada cuenta, sin confirmar.
     *
     * Un pendiente que llego sin cuenta se aprueba con la cuenta y la categoria de eleccion, que en uno con cuenta
     * sobran (FIN-66). Si es la parte de un gasto compartido, aprobarla es pagarla.
     */
    Mono<Transaction> approve(UUID userId, UUID transactionId, ApprovalChoice eleccion);

    /** Borra un pendiente. Inexistente o de otro usuario: 404; ya confirmado: 409. */
    Mono<Void> reject(UUID userId, UUID transactionId);

    /**
     * Borra esos pendientes del usuario, todos o ninguno (FIN-68), con el efecto del rechazo individual sobre cada uno.
     * Un id que no es del usuario: 404 en ids[i]; uno confirmado: 409 en ids[i].
     */
    Mono<Void> rejectBatch(UUID userId, List<UUID> ids);

    /**
     * Confirma todo lo que sigue pendiente del grupo, o nada, y lo devuelve CONFIRMED en el orden en que entro (FIN-58).
     * Sin movimientos del usuario en el grupo: 404 sobre groupId; con movimientos pero ninguno pendiente: 409 sobre
     * status. Las reglas de aprobar uno a uno fallan con 409 y el indice del pendiente en el grupo.
     */
    Flux<Transaction> approveGroup(UUID userId, UUID groupId);

    /**
     * Borra todo lo que sigue pendiente del grupo (FIN-58); lo ya confirmado no cambia. Los mismos 404 y 409 que
     * confirmar el grupo.
     */
    Mono<Void> rejectGroup(UUID userId, UUID groupId);
}
