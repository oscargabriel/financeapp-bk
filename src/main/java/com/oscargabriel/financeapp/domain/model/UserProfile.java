package com.oscargabriel.financeapp.domain.model;

import java.util.UUID;

/**
 * Lo que el usuario ve y edita de si mismo. Deliberadamente no es {@link User}: no lleva el hash, asi
 * que ninguna lectura del perfil puede devolverlo por descuido.
 */
public record UserProfile(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String baseCurrencyCode,
        String timezone) {
}
