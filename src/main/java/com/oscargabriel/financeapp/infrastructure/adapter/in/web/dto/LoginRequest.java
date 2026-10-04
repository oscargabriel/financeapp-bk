package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

import com.oscargabriel.financeapp.domain.model.LoginCommand;

/**
 * Un campo ausente es un error de forma, no un intento fallido: reportarlo como 400 no revela nada
 * sobre el usuario, y un 401 dejaria al cliente adivinando por que no entra.
 */
public record LoginRequest(

        @NotBlank(message = "El correo es obligatorio")
        String email,

        @NotBlank(message = "La contrasena es obligatoria")
        String password) {

    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
