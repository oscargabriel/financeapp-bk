package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.oscargabriel.financeapp.support.UserMother;
import com.oscargabriel.financeapp.support.Violaciones;

class ChangePasswordRequestTest {

    @Test
    void unCambioCompletoNoTieneViolaciones() {
        assertThat(Violaciones.de(new ChangePasswordRequest(UserMother.PASSWORD, "otraClaveLarga"))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void lasDosSonObligatorias(String vacio) {
        assertThat(Violaciones.de(new ChangePasswordRequest(vacio, vacio))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "currentPassword", List.of("La contrasena actual es obligatoria"),
                "newPassword", List.of("La contrasena nueva es obligatoria")));
    }

    @Test
    void laNuevaNecesitaOchoCaracteres() {
        assertThat(Violaciones.de(new ChangePasswordRequest(UserMother.PASSWORD, "corta12")))
                .containsExactly(Map.entry("newPassword", List.of("La contrasena debe tener al menos 8 caracteres")));
    }

    /** El mismo tope de BCrypt que el alta, medido en bytes. */
    @Test
    void laNuevaNoPuedePasarDe72Bytes() {
        assertThat(Violaciones.de(new ChangePasswordRequest(UserMother.PASSWORD, "ñ".repeat(37))))
                .containsExactly(Map.entry("newPassword", List.of("La contrasena no puede superar los 72 bytes")));
    }

    /** La actual no se valida por formato: una clave vieja de 7 caracteres tiene que poder compararse. */
    @Test
    void laActualNoTieneReglasDeFormato() {
        assertThat(Violaciones.de(new ChangePasswordRequest("corta12", "otraClaveLarga"))).isEmpty();
    }
}
