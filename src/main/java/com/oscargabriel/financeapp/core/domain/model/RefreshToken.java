package com.oscargabriel.financeapp.core.domain.model;

import java.time.Duration;

/**
 * El refresh que recibe el cliente y cuanto le queda a su sesion. Es el unico momento en que el
 * valor existe en claro: la base guarda solo su hash.
 */
public record RefreshToken(String value, Duration expiresIn) {
}
