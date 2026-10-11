package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.util.stream.Stream;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ZonaIana;

/**
 * El parche de PATCH /users/me: todo opcional, y null es "no cambia".
 *
 * No tiene componente baseCurrencyCode a proposito: Jackson descarta la propiedad como cualquier
 * otra desconocida, asi que el cambio de moneda se omite sin error hasta FA-91. Las cuentas hacen
 * lo contrario con los campos que no se cambian; aqui lo decidio el usuario el 07-10-2026 (ver el
 * design.md de fa-89-perfil-usuario).
 */
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

        /** Sin reglas de formato: solo se compara contra el hash, y solo si el correo cambia. */
        String currentPassword) {

    /** currentPassword no cuenta: sin un campo que cambiar, no hay nada que autorizar. */
    public boolean sinCambios() {
        return Stream.of(firstName, lastName, email, phone, timezone).allMatch(campo -> campo == null);
    }

    public UpdateUserProfileCommand toCommand() {
        return new UpdateUserProfileCommand(firstName, lastName, email, phone, timezone, currentPassword);
    }
}
