package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.infrastructure.adapter.in.web.UsuarioDelToken;
import com.oscargabriel.financeapp.core.domain.model.AffiliationAction;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.port.in.ChangeAffiliationPort;
import com.oscargabriel.financeapp.core.domain.port.in.ListAffiliationsPort;
import com.oscargabriel.financeapp.core.domain.port.in.SendAffiliationRequestPort;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.AffiliationResponse;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.SendAffiliationRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Solicitudes de afiliacion entre usuarios por correo (FIN-65). */
@RestController
@RequestMapping("/affiliations")
@AllArgsConstructor
public class AffiliationController {

    private final SendAffiliationRequestPort sendRequest;
    private final ListAffiliationsPort listAffiliations;
    private final ChangeAffiliationPort changeAffiliation;

    @GetMapping
    public Flux<AffiliationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return Flux.defer(() -> listAffiliations.list(UsuarioDelToken.de(jwt)))
                .map(AffiliationResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AffiliationResponse> send(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendAffiliationRequest solicitud) {
        return Mono.defer(() -> sendRequest.send(UsuarioDelToken.de(jwt), solicitud.email()))
                .map(AffiliationResponse::from);
    }

    /** Aceptar devuelve la relacion: sigue en el listado y ahora trae el nombre de la otra persona. */
    @PostMapping("/{id}/accept")
    public Mono<AffiliationResponse> accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return cambiar(jwt, id, AffiliationAction.ACCEPT).map(AffiliationResponse::from);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return cambiar(jwt, id, AffiliationAction.REJECT).then();
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return cambiar(jwt, id, AffiliationAction.CANCEL).then();
    }

    @PostMapping("/{id}/unlink")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unlink(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return cambiar(jwt, id, AffiliationAction.UNLINK).then();
    }

    private Mono<AffiliationView> cambiar(Jwt jwt, String id, AffiliationAction accion) {
        return Mono.defer(() -> changeAffiliation.change(UsuarioDelToken.de(jwt), parseId(id), accion));
    }

    /** A mano y no como UUID de Spring: su conversion fallida saldria como JSON_PARSING_ERROR del cuerpo. */
    private static UUID parseId(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    "El id de la afiliacion debe ser un UUID", "id", e);
        }
    }
}
