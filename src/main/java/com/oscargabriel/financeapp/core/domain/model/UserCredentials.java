package com.oscargabriel.financeapp.core.domain.model;

import java.util.UUID;

/**
 * Lo minimo que el login necesita saber de un usuario. Deliberadamente no es {@link User}: una
 * consulta de autenticacion no tiene por que traerse el nombre ni las preferencias.
 * {@code tokenVersion} es la version de sesion que llevan los tokens que se le emitan (FIN-47).
 * {@code passwordHash} es nulo si se registro con Google, y {@code googleSub} si no tiene cuenta
 * Google vinculada (FIN-52).
 */
public record UserCredentials(UUID id, String passwordHash, long tokenVersion, String googleSub) {
}
