package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;
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

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.in.CancelRecurrencePort;
import com.oscargabriel.financeapp.domain.port.in.CreateRecurrencePort;
import com.oscargabriel.financeapp.domain.port.in.ListRecurrencesPort;
import com.oscargabriel.financeapp.domain.port.in.UpdateRecurrencePort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CreateRecurrenceRequest;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.RecurrenceResponse;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.UpdateRecurrenceRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Series de gastos o ingresos que se repiten (FA-107). */
@RestController
@RequestMapping("/recurrences")
@AllArgsConstructor
public class RecurrenceController {

    private final CreateRecurrencePort createRecurrence;
    private final ListRecurrencesPort listRecurrences;
    private final UpdateRecurrencePort updateRecurrence;
    private final CancelRecurrencePort cancelRecurrence;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RecurrenceResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRecurrenceRequest alta) {
        return Mono.defer(() -> createRecurrence.create(UsuarioDelToken.de(jwt), alta.toCommand()))
                .map(RecurrenceResponse::from);
    }

    @GetMapping
    public Flux<RecurrenceResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return Flux.defer(() -> listRecurrences.list(UsuarioDelToken.de(jwt)))
                .map(RecurrenceResponse::from);
    }

    @PatchMapping("/{id}")
    public Mono<RecurrenceResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @Valid @RequestBody UpdateRecurrenceRequest parche) {
        return Mono.defer(() -> {
                    UUID serie = parseId(id);
                    if (parche.sinCambios()) {
                        throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                                "El parche no trae ningun campo para modificar, ademas del alcance", "body");
                    }
                    return updateRecurrence.update(UsuarioDelToken.de(jwt), serie, parche.toCommand());
                })
                .map(RecurrenceResponse::from);
    }

    /** Cancelar siempre conserva lo pasado: no lleva alcance. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return Mono.defer(() -> cancelRecurrence.cancel(UsuarioDelToken.de(jwt), parseId(id)));
    }

    /** A mano y no como UUID de Spring: su conversion fallida saldria como JSON_PARSING_ERROR del cuerpo. */
    private static UUID parseId(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    "El id de la serie debe ser un UUID", "id", e);
        }
    }
}
