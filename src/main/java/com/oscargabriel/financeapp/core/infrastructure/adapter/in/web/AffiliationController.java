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
import com.oscargabriel.financeapp.core.domain.port.in.AffiliationPort;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.AffiliateBalanceResponse;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.AffiliationResponse;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.SendAffiliationRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.ShareWithAffiliateResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Solicitudes de afiliacion entre usuarios por correo (FIN-65), y lo que cada persona y el usuario se deben (FIN-67). */
@RestController
@RequestMapping("/affiliations")
@AllArgsConstructor
public class AffiliationController {

    private final AffiliationPort afiliaciones;

    @GetMapping
    public Flux<AffiliationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return Flux.defer(() -> afiliaciones.list(UsuarioDelToken.de(jwt)))
                .map(AffiliationResponse::from);
    }

    @GetMapping("/balances")
    public Flux<AffiliateBalanceResponse> balances(@AuthenticationPrincipal Jwt jwt) {
        return Flux.defer(() -> afiliaciones.balances(UsuarioDelToken.de(jwt)))
                .map(AffiliateBalanceResponse::from);
    }

    @GetMapping("/{id}/shares")
    public Flux<ShareWithAffiliateResponse> shares(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return Flux.defer(() -> afiliaciones.shares(UsuarioDelToken.de(jwt), parseId(id)))
                .map(ShareWithAffiliateResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AffiliationResponse> send(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendAffiliationRequest solicitud) {
        return Mono.defer(() -> afiliaciones.send(UsuarioDelToken.de(jwt), solicitud.email()))
                .map(AffiliationResponse::from);
    }

    /** Aceptar devuelve la relacion: sigue en el listado y ahora trae el nombre de la otra persona. */
    @PostMapping("/{id}/accept")
    public Mono<AffiliationResponse> accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return Mono.defer(() -> afiliaciones.accept(UsuarioDelToken.de(jwt), parseId(id)))
                .map(AffiliationResponse::from);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return Mono.defer(() -> afiliaciones.reject(UsuarioDelToken.de(jwt), parseId(id))).then();
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return Mono.defer(() -> afiliaciones.cancel(UsuarioDelToken.de(jwt), parseId(id))).then();
    }

    @PostMapping("/{id}/unlink")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unlink(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return Mono.defer(() -> afiliaciones.unlink(UsuarioDelToken.de(jwt), parseId(id))).then();
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
