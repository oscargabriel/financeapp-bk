package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.domain.model.AccessToken;

/**
 * expiresIn va en segundos, como manda RFC 6749 para este campo: el cliente no tiene que parsear
 * el token ni confiar en su propio reloj para saber cuanto le queda.
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {

    private static final String TIPO = "Bearer";

    public static LoginResponse from(AccessToken token) {
        return new LoginResponse(token.value(), TIPO, token.expiresIn().toSeconds());
    }
}
