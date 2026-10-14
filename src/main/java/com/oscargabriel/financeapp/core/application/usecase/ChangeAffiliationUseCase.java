package com.oscargabriel.financeapp.core.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.AffiliationAction;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.port.in.ChangeAffiliationPort;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliationRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class ChangeAffiliationUseCase implements ChangeAffiliationPort {

    private final AffiliationRepositoryPort afiliaciones;

    /**
     * Lee antes de escribir para distinguir el 404 de una ajena del 409 de una accion que no aplica. La
     * escritura va condicionada al estado leido: si otra peticion la cambio en medio, tambien es 409.
     */
    @Override
    public Mono<AffiliationView> change(UUID userId, UUID affiliationId, AffiliationAction action) {
        return Mono.defer(() -> afiliaciones.findForParticipant(affiliationId, userId))
                .switchIfEmpty(Mono.error(ChangeAffiliationUseCase::noEncontrada))
                .flatMap(relacion -> action.appliesTo(relacion, userId)
                        ? afiliaciones.changeStatus(affiliationId, action.from(), action.to())
                        : Mono.just(false))
                .flatMap(cambiada -> cambiada
                        ? afiliaciones.findViewForParticipant(affiliationId, userId)
                        : Mono.error(estadoInvalido(action)));
    }

    private static BadRequestException noEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND,
                "La solicitud de afiliacion no existe", "id");
    }

    private static BadRequestException estadoInvalido(AffiliationAction action) {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.INVALID_STATE,
                switch (action) {
                    case ACCEPT -> "Solo quien recibe una solicitud abierta puede aceptarla";
                    case REJECT -> "Solo quien recibe una solicitud abierta puede rechazarla";
                    case CANCEL -> "Solo quien envia una solicitud abierta puede cancelarla";
                    case UNLINK -> "Solo se puede desvincular una afiliacion aceptada";
                }, "status");
    }
}
