package com.oscargabriel.financeapp.domain.model;

import java.util.UUID;

/**
 * Lo minimo que el login necesita saber de un usuario. Deliberadamente no es {@link User}: una
 * consulta de autenticacion no tiene por que traerse el nombre ni las preferencias.
 */
public record UserCredentials(UUID id, String passwordHash) {
}
