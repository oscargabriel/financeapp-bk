package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.oscargabriel.financeapp.core.domain.model.FirstPasswordCommand;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation.MaxBytesUtf8;

/**
 * Cuerpo de POST /users/me/password (FIN-53): el ID token de Google que confirma la identidad y la
 * primera contrasena, con las reglas del alta.
 */
public record FirstPasswordRequest(

        @NotBlank(message = "El token de Google es obligatorio")
        String idToken,

        @NotBlank(message = "La contrasena nueva es obligatoria")
        @Pattern(regexp = Formatos.AL_MENOS_8, message = "La contrasena debe tener al menos 8 caracteres")
        @MaxBytesUtf8(value = 72, message = "La contrasena no puede superar los 72 bytes")
        String newPassword) {

    public FirstPasswordCommand toCommand() {
        return new FirstPasswordCommand(idToken, newPassword);
    }
}
