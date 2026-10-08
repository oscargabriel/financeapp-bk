package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.MaxBytesUtf8;

/**
 * Cuerpo de PUT /users/me/password. La nueva sigue las reglas del alta. La actual no tiene reglas de
 * formato: solo se compara contra el hash, y una clave anterior a las reglas tiene que poder compararse.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "La contrasena actual es obligatoria")
        String currentPassword,

        @NotBlank(message = "La contrasena nueva es obligatoria")
        @Pattern(regexp = Formatos.AL_MENOS_8, message = "La contrasena debe tener al menos 8 caracteres")
        @MaxBytesUtf8(value = 72, message = "La contrasena no puede superar los 72 bytes")
        String newPassword) {
}
