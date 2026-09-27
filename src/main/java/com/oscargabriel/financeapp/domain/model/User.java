package com.oscargabriel.financeapp.domain.model;

import java.util.UUID;

/**
 * Usuario ya listo para persistir: la contrasena solo viaja hasta aqui como hash. Ningun punto del
 * flujo posterior al caso de uso conoce la clave en claro.
 */
public record User(
        UUID id,
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        String baseCurrencyCode,
        String timezone) {
}
