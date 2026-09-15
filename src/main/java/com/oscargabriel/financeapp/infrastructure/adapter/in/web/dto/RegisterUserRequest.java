package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.domain.model.RegistrationCommand;

/**
 * Cuerpo del alta. baseCurrencyCode y timezone son opcionales: el caso de uso los completa con el
 * mismo valor que finance.users declara como DEFAULT.
 *
 * No valida nada: la validacion entera vive en el caso de uso, que la reporta campo por campo.
 */
public record RegisterUserRequest(
        String email,
        String password,
        String firstName,
        String lastName,
        String baseCurrencyCode,
        String timezone) {

    public RegistrationCommand toCommand() {
        return new RegistrationCommand(
                email, password, firstName, lastName, baseCurrencyCode, timezone);
    }
}
