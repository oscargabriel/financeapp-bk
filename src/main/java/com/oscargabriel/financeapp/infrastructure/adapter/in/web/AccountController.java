package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.in.CreateAccountPort;
import com.oscargabriel.financeapp.domain.port.in.ListAccountsPort;
import com.oscargabriel.financeapp.domain.port.in.UpdateAccountPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.AccountResponse;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CreateAccountRequest;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.UpdateAccountRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/accounts")
@AllArgsConstructor
public class AccountController {

    private final ListAccountsPort listAccounts;
    private final CreateAccountPort createAccount;
    private final UpdateAccountPort updateAccount;

    @GetMapping
    public Flux<AccountResponse> accounts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String includeInactive) {
        return Flux.defer(() -> listAccounts.list(UsuarioDelToken.de(jwt), parseIncludeInactive(includeInactive)))
                .map(AccountResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AccountResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAccountRequest request) {
        return Mono.defer(() -> createAccount.create(UsuarioDelToken.de(jwt), request.toCommand()))
                .map(AccountResponse::from);
    }

    @PatchMapping("/{id}")
    public Mono<AccountResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody UpdateAccountRequest parche) {
        return Mono.defer(() -> {
                    UUID cuenta = parseId(id);
                    if (parche.sinCambios()) {
                        throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                                "El parche no trae ningun campo para modificar", "body");
                    }
                    return updateAccount.update(UsuarioDelToken.de(jwt), cuenta, parche.toCommand());
                })
                .map(AccountResponse::from);
    }

    /** A mano y no como UUID de Spring: su conversion fallida saldria como JSON_PARSING_ERROR del cuerpo. */
    private static UUID parseId(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    "El id de la cuenta debe ser un UUID", "id", e);
        }
    }

    /** Solo true o false: Boolean.parseBoolean tomaria cualquier otra cosa como false sin avisar. */
    private static boolean parseIncludeInactive(String valor) {
        if (valor == null || valor.isBlank() || valor.equals("false")) {
            return false;
        }
        if (valor.equals("true")) {
            return true;
        }
        throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "includeInactive debe ser true o false", "includeInactive");
    }
}
