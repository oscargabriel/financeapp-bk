package com.oscargabriel.financeapp.core.domain.port.out;

/**
 * Genera el valor del refresh y el hash con el que se guarda y se busca. Sincrono, como
 * TokenIssuerPort: los dos son calculos.
 */
public interface RefreshTokenGeneratorPort {

    /** Un valor aleatorio, opaco y apto para viajar en JSON sin escapar. */
    String generar();

    /** Determinista: el mismo valor da siempre el mismo hash, que no permite reconstruirlo. */
    String hash(String refreshToken);
}
