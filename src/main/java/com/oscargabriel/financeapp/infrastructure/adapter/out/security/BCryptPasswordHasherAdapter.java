package com.oscargabriel.financeapp.infrastructure.adapter.out.security;

import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.port.out.PasswordHasherPort;

/** Reusa el PasswordEncoder que ya declara SecurityConfig, para que haya un solo algoritmo. */
@Component
@AllArgsConstructor
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }

    @Override
    public boolean matches(String plainPassword, String passwordHash) {
        return passwordEncoder.matches(plainPassword, passwordHash);
    }
}
