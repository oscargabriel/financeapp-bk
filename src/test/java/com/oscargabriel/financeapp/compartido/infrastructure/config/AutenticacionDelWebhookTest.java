package com.oscargabriel.financeapp.compartido.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.oscargabriel.financeapp.telegram.support.TelegramMother;

/**
 * El formato del secreto se valida al construir la cadena: un secreto que Telegram no aceptaria en
 * setWebhook, o demasiado corto, deja el contexto abajo en el arranque.
 */
class AutenticacionDelWebhookTest {

    @Test
    void aceptaElSecretoDeLaSuite() {
        assertThat(AutenticacionDelWebhook.validado(TelegramMother.SECRETO)).isEqualTo(TelegramMother.SECRETO);
    }

    @Test
    void aceptaLosLimitesDe32Y256Caracteres() {
        assertThat(AutenticacionDelWebhook.validado("a".repeat(32))).hasSize(32);
        assertThat(AutenticacionDelWebhook.validado("A_-9".repeat(64))).hasSize(256);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "secreto-de-31-caracteres-012345",
            "secreto-con-un-espacio en-medio-de-32",
            "secreto-con-un-signo-raro!-de-mas-de-32",
            "secreto-con-tilde-ñ-de-mas-de-32-caracteres"
    })
    void rechazaUnSecretoCortoOConCaracteresQueTelegramNoAcepta(String secreto) {
        assertThatThrownBy(() -> AutenticacionDelWebhook.validado(secreto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("telegram.webhook-secret")
                .hasMessageNotContaining(secreto);
    }

    @Test
    void rechazaUnSecretoDeMasDe256Caracteres() {
        assertThatThrownBy(() -> AutenticacionDelWebhook.validado("a".repeat(257)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechazaUnSecretoVacio() {
        assertThatThrownBy(() -> AutenticacionDelWebhook.validado(""))
                .isInstanceOf(IllegalStateException.class);
    }
}
