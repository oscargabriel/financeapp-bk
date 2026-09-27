package com.oscargabriel.financeapp.domain.port.out;

/**
 * Aisla al caso de uso del algoritmo de hash y de Spring Security. Cambiar BCrypt por Argon2 no
 * deberia tocar la logica del registro.
 */
public interface PasswordHasherPort {

    String hash(String plainPassword);

    /**
     * El hash nunca se compara con equals: lleva la sal dentro, asi que la misma clave produce
     * uno distinto cada vez y solo el algoritmo sabe verificarlo.
     */
    boolean matches(String plainPassword, String passwordHash);
}
