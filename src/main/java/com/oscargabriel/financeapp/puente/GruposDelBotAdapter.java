package com.oscargabriel.financeapp.puente;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.core.domain.port.in.TransactionPort;
import com.oscargabriel.financeapp.telegram.domain.exceptions.GrupoYaNoPendienteException;
import com.oscargabriel.financeapp.telegram.domain.port.out.GruposDelBotPort;

import reactor.core.publisher.Mono;

/**
 * Puente telegram → core (FIN-61): los botones del chat confirman o descartan el grupo con las operaciones del core.
 * La regla de que es "ya no pendiente" es la del core, copiada como traduccion (design.md de FIN-61, decision 5).
 */
@Component
@AllArgsConstructor
public class GruposDelBotAdapter implements GruposDelBotPort {

    private final TransactionPort transacciones;

    @Override
    public Mono<Integer> confirmar(UUID usuario, UUID grupo) {
        return transacciones.approveGroup(usuario, grupo).count()
                .map(Long::intValue)
                .onErrorMap(GruposDelBotAdapter::yaNoPendiente, GrupoYaNoPendienteException::new);
    }

    @Override
    public Mono<Void> descartar(UUID usuario, UUID grupo) {
        return transacciones.rejectGroup(usuario, grupo)
                .onErrorMap(GruposDelBotAdapter::yaNoPendiente, GrupoYaNoPendienteException::new);
    }

    /**
     * El 404 (sin movimientos del usuario en el grupo) y el 409 sobre status (ninguno sigue pendiente). Un 409 de una
     * regla del alta lleva el indice del pendiente en el field y no es esto.
     */
    private static boolean yaNoPendiente(Throwable error) {
        return error instanceof BadRequestException rechazo
                && (rechazo.getHttpStatus() == HttpStatus.NOT_FOUND
                        || rechazo.getHttpStatus() == HttpStatus.CONFLICT && rechazo.getErrorResponse().getErrors()
                                .stream().anyMatch(detalle -> "status".equals(detalle.getField())));
    }
}
