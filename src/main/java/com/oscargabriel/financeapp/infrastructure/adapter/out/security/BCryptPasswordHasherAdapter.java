package com.oscargabriel.financeapp.infrastructure.adapter.out.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.port.out.PasswordHasherPort;

/** Reusa el PasswordEncoder que ya declara SecurityConfig, para que haya un solo algoritmo. */
@Component
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasherAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }
}
