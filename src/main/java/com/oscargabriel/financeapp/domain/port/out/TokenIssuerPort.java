package com.oscargabriel.financeapp.domain.port.out;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.AccessToken;

/**
 * Aisla al caso de uso del formato del token y de su firma. Es sincrono a proposito: emitir es
 * un calculo, no una llamada a un sistema externo.
 */
public interface TokenIssuerPort {

    AccessToken issueFor(UUID userId);
}
