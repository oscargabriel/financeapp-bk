package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.core.domain.model.SessionTokens;

/**
 * La respuesta del login y del refresco. expiresIn va en segundos, como manda RFC 6749 para este
 * campo: el cliente no tiene que parsear el token ni confiar en su propio reloj para saber cuanto
 * le queda. refreshExpiresIn es lo mismo para la sesion entera (FIN-47).
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn) {

    private static final String TIPO = "Bearer";

    public static TokenResponse from(SessionTokens tokens) {
        return new TokenResponse(tokens.access().value(), TIPO, tokens.access().expiresIn().toSeconds(),
                tokens.refresh().value(), tokens.refresh().expiresIn().toSeconds());
    }
}
