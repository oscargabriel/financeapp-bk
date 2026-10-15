package com.oscargabriel.financeapp.ia.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AssistantFileTypeTest {

    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10);
    private static final byte[] OGG = ascii("OggS\0\2\0\0");

    static Stream<Arguments> firmas() {
        return Stream.of(
                Arguments.of("image/jpeg", JPEG, AssistantFileType.JPEG),
                Arguments.of("image/png", bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00), AssistantFileType.PNG),
                Arguments.of("image/webp", ascii("RIFF\1\2\3\4WEBPVP8 "), AssistantFileType.WEBP),
                Arguments.of("image/heic", ascii("\0\0\0\30ftypheic\0\0\0\0"), AssistantFileType.HEIC),
                Arguments.of("image/heif", ascii("\0\0\0\30ftypmif1\0\0\0\0"), AssistantFileType.HEIF),
                Arguments.of("audio/ogg", OGG, AssistantFileType.OGG),
                Arguments.of("audio/mpeg", ascii("ID3\4\0\0"), AssistantFileType.MP3),
                Arguments.of("audio/mpeg", bytes(0xFF, 0xFB, 0x90, 0x64), AssistantFileType.MP3),
                Arguments.of("audio/wav", ascii("RIFF\1\2\3\4WAVEfmt "), AssistantFileType.WAV),
                Arguments.of("audio/aac", bytes(0xFF, 0xF1, 0x50, 0x80), AssistantFileType.AAC),
                Arguments.of("audio/aac", bytes(0xFF, 0xF9, 0x50, 0x80), AssistantFileType.AAC),
                Arguments.of("audio/flac", ascii("fLaC\0\0\0\42"), AssistantFileType.FLAC));
    }

    @ParameterizedTest
    @MethodSource("firmas")
    void reconoceCadaTipoPorSuMimeYSuFirma(String mime, byte[] contenido, AssistantFileType esperado) {
        assertThat(AssistantFileType.de(mime, contenido)).contains(esperado);
    }

    @Test
    void elMimeNoDistingueMayusculasNiTraeParametros() {
        assertThat(AssistantFileType.de("Audio/OGG; codecs=opus", OGG)).contains(AssistantFileType.OGG);
    }

    @ParameterizedTest
    @ValueSource(strings = {"video/mp4", "application/pdf", "text/plain", "image/gif", ""})
    void rechazaUnTipoFueraDeLaLista(String mime) {
        assertThat(AssistantFileType.de(mime, JPEG)).isEmpty();
    }

    @Test
    void rechazaUnMimeAusente() {
        assertThat(AssistantFileType.de(null, JPEG)).isEmpty();
    }

    @Test
    void rechazaUnContenidoQueNoEsDelTipoDeclarado() {
        assertThat(AssistantFileType.de("image/jpeg", OGG)).isEmpty();
    }

    @Test
    void rechazaUnVideoMp4DeclaradoComoImagenHeic() {
        assertThat(AssistantFileType.de("image/heic", ascii("\0\0\0\30ftypisom\0\0\0\0"))).isEmpty();
    }

    @Test
    void rechazaBytesMasCortosQueLaFirma() {
        assertThat(AssistantFileType.de("image/png", bytes(0x89, 0x50))).isEmpty();
    }

    private static byte[] bytes(int... valores) {
        byte[] resultado = new byte[valores.length];
        for (int i = 0; i < valores.length; i++) {
            resultado[i] = (byte) valores[i];
        }
        return resultado;
    }

    private static byte[] ascii(String texto) {
        return texto.getBytes(StandardCharsets.ISO_8859_1);
    }
}
