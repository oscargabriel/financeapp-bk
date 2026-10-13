package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import java.util.stream.Stream;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.core.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation.ZonaIana;

/** El parche de PATCH /users/me: todo opcional, y null es "no cambia". */
public record UpdateUserProfileRequest(

        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "El nombre no puede ir en blanco: para no cambiarlo, omitelo")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String firstName,

        /** En blanco borra el apellido: es opcional en la tabla. */
        @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
        String lastName,

        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "El correo no puede ir en blanco: para no cambiarlo, omitelo")
        @Size(max = 255, message = "El correo no tiene un formato valido")
        @Pattern(regexp = Formatos.EMAIL, message = "El correo no tiene un formato valido")
        String email,

        /** En blanco borra el celular, como el apellido. */
        @Pattern(regexp = Formatos.CELULAR, message = Formatos.MENSAJE_CELULAR)
        String phone,

        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "La zona horaria no puede ir en blanco: para no cambiarla, omitela")
        @ZonaIana
        String timezone,

        /** Que exista y este activa lo decide el caso de uso, que normaliza (FIN-29). */
        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "La moneda no puede ir en blanco: para no cambiarla, omitela")
        @Pattern(regexp = Formatos.CODIGO_MONEDA, message = "La moneda debe ser un codigo de tres letras")
        String baseCurrencyCode,

        /** Sin reglas de formato: solo se compara contra el hash, y solo si el correo cambia. */
        String currentPassword) {

    /** currentPassword no cuenta: sin un campo que cambiar, no hay nada que autorizar. */
    public boolean sinCambios() {
        return Stream.of(firstName, lastName, email, phone, timezone, baseCurrencyCode)
                .allMatch(campo -> campo == null);
    }

    public UpdateUserProfileCommand toCommand() {
        return new UpdateUserProfileCommand(
                firstName, lastName, email, phone, timezone, baseCurrencyCode, currentPassword);
    }
}
