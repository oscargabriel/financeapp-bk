package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.MaxBytesUtf8;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ZonaIana;

/**
 * Cuerpo del alta. baseCurrencyCode y timezone son opcionales: el caso de uso los completa con el
 * mismo valor que finance.users declara como DEFAULT.
 *
 * Aqui va el formato; que la moneda exista y que el correo no este registrado lo decide el caso de uso.
 */
public record RegisterUserRequest(

        @NotBlank(message = "El correo es obligatorio")
        @Size(max = 255, message = "El correo no tiene un formato valido")
        @Pattern(regexp = Formatos.EMAIL, message = "El correo no tiene un formato valido")
        String email,

        @NotBlank(message = "La contrasena es obligatoria")
        @Pattern(regexp = Formatos.AL_MENOS_8, message = "La contrasena debe tener al menos 8 caracteres")
        @MaxBytesUtf8(value = 72, message = "La contrasena no puede superar los 72 bytes")
        String password,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String firstName,

        @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
        String lastName,

        @Pattern(regexp = Formatos.CODIGO_MONEDA, message = "La moneda debe ser un codigo de tres letras")
        String baseCurrencyCode,

        @ZonaIana
        String timezone) {

    public RegistrationCommand toCommand() {
        return new RegistrationCommand(
                email, password, firstName, lastName, baseCurrencyCode, timezone);
    }
}
