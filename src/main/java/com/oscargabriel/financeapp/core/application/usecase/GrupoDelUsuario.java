package com.oscargabriel.financeapp.core.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;

/** La lectura que confirmar y descartar un grupo comparten antes de escribir: el 404 y el 409 (FIN-58). */
final class GrupoDelUsuario {

    private GrupoDelUsuario() {
    }

    /**
     * Lo que sigue pendiente del grupo, en el orden en que entro. Sin movimientos del usuario en el grupo (no existe,
     * es de otro o ya se descarto) es 404; con movimientos pero ninguno pendiente, ya se confirmo: 409.
     */
    static Mono<List<Transaction>> pendientes(TransactionRepositoryPort repositorio, UUID groupId, UUID userId) {
        return repositorio.findByGroup(groupId, userId).collectList()
                .flatMap(grupo -> {
                    if (grupo.isEmpty()) {
                        return Mono.error(noEncontrado());
                    }
                    List<Transaction> pendientes = grupo.stream()
                            .filter(t -> t.status() == TransactionStatus.PENDING).toList();
                    return pendientes.isEmpty() ? Mono.error(yaConfirmado()) : Mono.just(pendientes);
                });
    }

    static BadRequestException noEncontrado() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "El grupo no existe", "groupId")));
    }

    private static BadRequestException yaConfirmado() {
        return new BadRequestException(HttpStatus.CONFLICT, List.of(ErrorDetail.of(
                ErrorCodes.INVALID_STATE.getCode(), "El grupo ya esta confirmado", "status")));
    }
}
