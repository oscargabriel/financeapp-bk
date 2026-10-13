package com.oscargabriel.financeapp.core.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.AccessToken;

/**
 * Aisla al caso de uso del formato del token y de su firma. Es sincrono a proposito: emitir es
 * un calculo, no una llamada a un sistema externo.
 */
public interface TokenIssuerPort {

    /** {@code tokenVersion} es la version de sesion del usuario al emitir: el token deja de servir si cambia. */
    AccessToken issueFor(UUID userId, long tokenVersion);
}
