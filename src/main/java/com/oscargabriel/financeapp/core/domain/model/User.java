package com.oscargabriel.financeapp.core.domain.model;

import java.util.UUID;

/**
 * Usuario ya listo para persistir: la contrasena solo viaja hasta aqui como hash. Ningun punto del
 * flujo posterior al caso de uso conoce la clave en claro. Un registro con Google llega sin hash y con
 * el sub de su cuenta (FIN-52); el alta normal, al reves.
 */
public record User(
        UUID id,
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        String baseCurrencyCode,
        String timezone,
        String phone,
        String googleSub) {
}
