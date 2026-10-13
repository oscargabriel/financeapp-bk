package com.oscargabriel.financeapp.core.domain.model;

/** Lo que entregan el login y el refresco: el access que autentica y el refresh que lo renueva. */
public record SessionTokens(AccessToken access, RefreshToken refresh) {
}
