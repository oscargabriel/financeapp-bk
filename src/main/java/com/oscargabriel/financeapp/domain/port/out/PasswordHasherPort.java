package com.oscargabriel.financeapp.domain.port.out;

/**
 * Aisla al caso de uso del algoritmo de hash y de Spring Security. Cambiar BCrypt por Argon2 no
 * deberia tocar la logica del registro.
 */
public interface PasswordHasherPort {

    String hash(String plainPassword);
}
