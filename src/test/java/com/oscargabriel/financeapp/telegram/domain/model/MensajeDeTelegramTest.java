package com.oscargabriel.financeapp.telegram.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.oscargabriel.financeapp.telegram.support.TelegramMother;

class MensajeDeTelegramTest {

    @ParameterizedTest
    @ValueSource(strings = {"/start", "/start abc123", " /start "})
    void esStartConOSinParametro(String texto) {
        assertThat(TelegramMother.texto(texto).esStart()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"/startx", "hola /start", "/stop", "start"})
    void noEsStartOtroTexto(String texto) {
        assertThat(TelegramMother.texto(texto).esStart()).isFalse();
    }

    @Test
    void unMensajeConTextoTieneContenido() {
        assertThat(TelegramMother.texto("gaste 45000").sinContenido()).isFalse();
    }

    @Test
    void unMensajeConArchivoYSinTextoTieneContenido() {
        assertThat(TelegramMother.conArchivo(null, TelegramMother.notaDeVoz()).sinContenido()).isFalse();
    }

    @Test
    void unContactoEsContenido() {
        assertThat(TelegramMother.privado(TelegramMother.contactoPropio(TelegramMother.TELEFONO)).sinContenido())
                .isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void sinTextoArchivoNiContactoNoTieneContenido(String texto) {
        assertThat(TelegramMother.texto(texto).sinContenido()).isTrue();
    }

    @Test
    void elArchivoNoMuestraSuFileId() {
        assertThat(TelegramMother.notaDeVoz().toString()).doesNotContain(TelegramMother.notaDeVoz().fileId());
    }
}
