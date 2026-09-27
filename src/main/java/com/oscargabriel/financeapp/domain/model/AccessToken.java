package com.oscargabriel.financeapp.domain.model;

import java.time.Duration;

/** Token emitido y cuanto dura. El dominio no sabe que por dentro es un JWT. */
public record AccessToken(String value, Duration expiresIn) {
}
