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

    @Test
    void reconoceLaClaveQueGeneroElHash() {
        assertThat(hasher.matches("unaClaveLarga", hasher.hash("unaClaveLarga"))).isTrue();
    }

    @Test
    void rechazaCualquierOtraClave() {
        assertThat(hasher.matches("otraClave", hasher.hash("unaClaveLarga"))).isFalse();
    }

    /**
     * El hash del escenario de docs/database/test-data.sql. Si alguien lo regenera sin actualizar
     * la clave documentada, los requests de login de bruno/auth/ se caen sin decir por que.
     */
    @Test
    void verificaElHashDelUsuarioDePruebas() {
        String hashDelEscenario = "$2a$10$a1kFiM14Uwu.ShxTcDB0seZDpwZFth4V8tIwytSj8jR46/UK1cAmy";

        assertThat(hasher.matches("claveDePrueba123", hashDelEscenario)).isTrue();
    }
}
