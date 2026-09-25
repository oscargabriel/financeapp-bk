package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.in.ListAccountsPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.AccountResponse;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final ListAccountsPort listAccounts;

    public AccountController(ListAccountsPort listAccounts) {
        this.listAccounts = listAccounts;
    }

    @GetMapping
    public Flux<AccountResponse> accounts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String includeInactive) {
        return Flux.defer(() -> listAccounts.list(UsuarioDelToken.de(jwt), parseIncludeInactive(includeInactive)))
                .map(AccountResponse::from);
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
