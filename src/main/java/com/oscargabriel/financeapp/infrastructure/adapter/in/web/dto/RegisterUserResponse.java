package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.domain.model.RegisteredUser;

/**
 * Respuesta del alta. No lleva passwordHash y no debe llevarlo nunca: se construye campo por campo
 * a proposito, para que agregar una columna a User no la filtre sin que nadie lo note.
 */
public record RegisterUserResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String baseCurrencyCode,
        String timezone,
        long defaultCategories) {

    public static RegisterUserResponse from(RegisteredUser registrado) {
        return new RegisterUserResponse(
                registrado.user().id().toString(),
                registrado.user().email(),
                registrado.user().firstName(),
                registrado.user().lastName(),
                registrado.user().baseCurrencyCode(),
                registrado.user().timezone(),
                registrado.defaultCategories());
    }
}
