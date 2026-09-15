package com.oscargabriel.financeapp.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class BCryptPasswordHasherAdapterTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private final BCryptPasswordHasherAdapter hasher = new BCryptPasswordHasherAdapter(encoder);

    @Test
    void devuelveUnHashVerificableQueNoContieneLaClave() {
        String hash = hasher.hash("unaClaveLarga");

        assertThat(hash).doesNotContain("unaClaveLarga");
        assertThat(encoder.matches("unaClaveLarga", hash)).isTrue();
    }

    /** La sal es distinta en cada llamada: dos usuarios con la misma clave no comparten hash. */
    @Test
    void noRepiteElHashParaLaMismaClave() {
        assertThat(hasher.hash("unaClaveLarga")).isNotEqualTo(hasher.hash("unaClaveLarga"));
    }
}
