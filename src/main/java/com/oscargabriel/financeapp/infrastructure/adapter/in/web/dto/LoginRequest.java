package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.domain.model.LoginCommand;

/** No valida nada: la validacion vive en el caso de uso, igual que en el alta. */
public record LoginRequest(String email, String password) {

    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
