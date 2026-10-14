package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.infrastructure.adapter.in.web.UsuarioDelToken;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.port.in.ApprovePendingGroupPort;
import com.oscargabriel.financeapp.core.domain.port.in.ApprovePendingTransactionPort;
import com.oscargabriel.financeapp.core.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.core.domain.port.in.DeleteTransactionPort;
import com.oscargabriel.financeapp.core.domain.port.in.ListPendingTransactionsPort;
import com.oscargabriel.financeapp.core.domain.port.in.RejectPendingGroupPort;
import com.oscargabriel.financeapp.core.domain.port.in.RejectPendingTransactionPort;
import com.oscargabriel.financeapp.core.domain.port.in.UpdateTransactionPort;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.CreateTransactionRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.TransactionResponse;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.UpdateTransactionRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/transactions")
@AllArgsConstructor
public class TransactionController {

    /**
     * El tope de FA-26: varios meses de extracto y, con elementos tipicos, bien por debajo del MB
     * del codec.
     */
    static final int TOPE_LOTE = 500;

    private final CreateTransactionsPort createTransactions;
    private final UpdateTransactionPort updateTransaction;
    private final DeleteTransactionPort deleteTransaction;
    private final ListPendingTransactionsPort listPending;
    private final ApprovePendingTransactionPort approvePending;
    private final RejectPendingTransactionPort rejectPending;
    private final ApprovePendingGroupPort approvePendingGroup;
    private final RejectPendingGroupPort rejectPendingGroup;
    private final Clock clock;

    /**
     * Mono de la lista y no Flux: transmitir el Flux mandaria el 201 y los primeros elementos antes de
     * que falle un INSERT posterior, y el cliente creeria que entraron aunque la base haga rollback.
     *
     * Las constraints del parametro activan la validacion de metodo de Spring: el tamano del lote, los
     * elementos nulos y las reglas de cada record salen juntos, cada uno con su indice.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<List<TransactionResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody
            @Size(min = 1, max = TOPE_LOTE, message = "El lote debe tener entre 1 y " + TOPE_LOTE + " movimientos")
            List<@NotNull(message = "El elemento no puede ser nulo") @Valid CreateTransactionRequest> lote) {
        return Flux.defer(() -> createTransactions.create(UsuarioDelToken.de(jwt), TransactionOrigin.WEB,
                        TransactionStatus.CONFIRMED, lote.stream().map(CreateTransactionRequest::toCommand).toList()))
                .map(t -> TransactionResponse.from(t, clock.instant()))
                .collectList();
    }

    @PatchMapping("/{id}")
    public Mono<TransactionResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody UpdateTransactionRequest parche) {
        return Mono.defer(() -> {
                    UUID movimiento = parseId(id);
                    if (parche.sinCambios()) {
                        throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                                "El parche no trae ningun campo para modificar", "body");
                    }
                    return updateTransaction.update(UsuarioDelToken.de(jwt), movimiento, parche.toCommand());
                })
                .map(t -> TransactionResponse.from(t, clock.instant()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return Mono.defer(() -> deleteTransaction.delete(UsuarioDelToken.de(jwt), parseId(id)));
    }

    /** No choca con /{id}: no hay GET sobre un movimiento. */
    @GetMapping("/pending")
    public Flux<TransactionResponse> pending(@AuthenticationPrincipal Jwt jwt) {
        return Flux.defer(() -> listPending.listPending(UsuarioDelToken.de(jwt)))
                .map(t -> TransactionResponse.from(t, clock.instant()));
    }

    @PostMapping("/{id}/approve")
    public Mono<TransactionResponse> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return Mono.defer(() -> approvePending.approve(UsuarioDelToken.de(jwt), parseId(id)))
                .map(t -> TransactionResponse.from(t, clock.instant()));
    }

    /** Solo un pendiente: sobre un confirmado es 409, para no borrarlo creyendo rechazar. */
    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return Mono.defer(() -> rejectPending.reject(UsuarioDelToken.de(jwt), parseId(id)));
    }

    /**
     * Confirma todo lo que sigue pendiente del grupo, o nada (FIN-58). Mono de la lista, como el alta: el 200 sale
     * solo si se confirmaron todos.
     */
    @PostMapping("/groups/{groupId}/approve")
    public Mono<List<TransactionResponse>> approveGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable String groupId) {
        return Flux.defer(() -> approvePendingGroup.approve(UsuarioDelToken.de(jwt), parseGroupId(groupId)))
                .map(t -> TransactionResponse.from(t, clock.instant()))
                .collectList();
    }

    @PostMapping("/groups/{groupId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> rejectGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable String groupId) {
        return Mono.defer(() -> rejectPendingGroup.reject(UsuarioDelToken.de(jwt), parseGroupId(groupId)));
    }

    /** A mano y no como UUID de Spring: su conversion fallida saldria como JSON_PARSING_ERROR del cuerpo. */
    private static UUID parseId(String valor) {
        return uuid(valor, "El id del movimiento debe ser un UUID", "id");
    }

    private static UUID parseGroupId(String valor) {
        return uuid(valor, "El id del grupo debe ser un UUID", "groupId");
    }

    private static UUID uuid(String valor, String descripcion, String campo) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, descripcion, campo, e);
        }
    }
}
