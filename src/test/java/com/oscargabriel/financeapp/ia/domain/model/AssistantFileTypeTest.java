package com.oscargabriel.financeapp.ia.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
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

    @Test
    void soloLosAudiosSonAudio() {
        assertThat(Arrays.stream(AssistantFileType.values()).filter(AssistantFileType::esAudio))
                .containsExactlyInAnyOrder(AssistantFileType.OGG, AssistantFileType.MP3, AssistantFileType.WAV,
                        AssistantFileType.AAC, AssistantFileType.FLAC, AssistantFileType.WEBM, AssistantFileType.MP4);
    }

    @Nested
    class Webm {

        private static final int CABECERA = 0x1A45DFA3;
        private static final int DOC_TYPE = 0x4282;
        private static final int SEGMENTO = 0x18538067;
        private static final int SEEK_HEAD = 0x114D9B74;
        private static final int SEEK_ID = 0x53AB;
        private static final int TRACKS = 0x1654AE6B;
        private static final int TRACK_ENTRY = 0xAE;
        private static final int TRACK_TYPE = 0x83;
        private static final int CLUSTER = 0x1F43B675;

        private static final byte[] PISTA_DE_AUDIO = ebml(TRACK_ENTRY, ebml(TRACK_TYPE, bytes(2)));
        private static final byte[] PISTA_DE_VIDEO = ebml(TRACK_ENTRY, ebml(TRACK_TYPE, bytes(1)));
        private static final byte[] UN_CLUSTER = ebml(CLUSTER, bytes(0xE7, 0x81, 0x00));

        @Test
        void reconoceUnWebmDeSoloAudio() {
            byte[] webm = webm("webm", ebml(SEGMENTO, ebml(TRACKS, PISTA_DE_AUDIO), UN_CLUSTER));

            assertThat(AssistantFileType.de("audio/webm", webm)).contains(AssistantFileType.WEBM);
        }

        @Test
        void elCodecDelTipoNoImporta() {
            byte[] webm = webm("webm", ebml(SEGMENTO, ebml(TRACKS, PISTA_DE_AUDIO)));

            assertThat(AssistantFileType.de("audio/webm;codecs=opus", webm)).contains(AssistantFileType.WEBM);
        }

        @Test
        void reconoceElWebmQueGrabaChrome() {
            byte[] webm = webm("webm", segmentoSinTamano(seekHeadQueNombraElCluster(), ebml(TRACKS, PISTA_DE_AUDIO),
                    clusterSinTamano()));

            assertThat(AssistantFileType.de("audio/webm", webm)).contains(AssistantFileType.WEBM);
        }

        @Test
        void rechazaUnMatroskaQueNoEsWebm() {
            byte[] mkv = webm("matroska", ebml(SEGMENTO, ebml(TRACKS, PISTA_DE_AUDIO)));

            assertThat(AssistantFileType.de("audio/webm", mkv)).isEmpty();
        }

        @Test
        void rechazaUnWebmConPistaDeVideo() {
            byte[] webm = webm("webm", ebml(SEGMENTO, ebml(TRACKS, PISTA_DE_AUDIO, PISTA_DE_VIDEO), UN_CLUSTER));

            assertThat(AssistantFileType.de("audio/webm", webm)).isEmpty();
        }

        @Test
        void unSeekHeadQueNombraElClusterNoEscondeLaPistaDeVideo() {
            byte[] webm = webm("webm", segmentoSinTamano(seekHeadQueNombraElCluster(),
                    ebml(TRACKS, PISTA_DE_AUDIO, PISTA_DE_VIDEO), clusterSinTamano()));

            assertThat(AssistantFileType.de("audio/webm", webm)).isEmpty();
        }

        @Test
        void rechazaUnClusterAntesDeLasPistas() {
            byte[] webm = webm("webm", ebml(SEGMENTO, UN_CLUSTER, ebml(TRACKS, PISTA_DE_VIDEO)));

            assertThat(AssistantFileType.de("audio/webm", webm)).isEmpty();
        }

        @Test
        void rechazaUnWebmSinSegmento() {
            assertThat(AssistantFileType.de("audio/webm", webm("webm"))).isEmpty();
        }

        @Test
        void rechazaUnWebmCortado() {
            assertThat(AssistantFileType.de("audio/webm", bytes(0x1A, 0x45, 0xDF, 0xA3, 0x9F, 0x42))).isEmpty();
        }

        private static byte[] webm(String docType, byte[]... resto) {
            return concatenar(ebml(CABECERA, ebml(DOC_TYPE, ascii(docType))), concatenar(resto));
        }

        private static byte[] seekHeadQueNombraElCluster() {
            return ebml(SEEK_HEAD, ebml(0x4DBB, ebml(SEEK_ID, bytes(0x1F, 0x43, 0xB6, 0x75)), ebml(0x53AC, bytes(0x81))));
        }

        private static byte[] segmentoSinTamano(byte[]... hijos) {
            return concatenar(bytes(0x18, 0x53, 0x80, 0x67, 0x01, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF),
                    concatenar(hijos));
        }

        private static byte[] clusterSinTamano() {
            return bytes(0x1F, 0x43, 0xB6, 0x75, 0x01, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xE7, 0x81, 0x00);
        }

        /** Un elemento EBML con el tamano en un byte: alcanza para los de estos tests, de menos de 127 bytes. */
        private static byte[] ebml(int id, byte[]... hijos) {
            byte[] datos = concatenar(hijos);
            int largoId = (39 - Integer.numberOfLeadingZeros(id)) / 8;
            byte[] resultado = new byte[largoId + 1 + datos.length];
            for (int i = 0; i < largoId; i++) {
                resultado[i] = (byte) (id >>> (8 * (largoId - 1 - i)));
            }
            resultado[largoId] = (byte) (0x80 | datos.length);
            System.arraycopy(datos, 0, resultado, largoId + 1, datos.length);
            return resultado;
        }
    }

    @Nested
    class Mp4 {

        private static final String HDLR_SOUN = "\0\0\0\41hdlr\0\0\0\0\0\0\0\0soun";
        private static final String HDLR_VIDE = "\0\0\0\41hdlr\0\0\0\0\0\0\0\0vide";

        @ParameterizedTest
        @ValueSource(strings = {"M4A ", "isom", "mp42"})
        void reconoceUnMp4DeSoloAudio(String marca) {
            byte[] mp4 = ascii("\0\0\0\30ftyp" + marca + "\0\0\0\0\0\0\0\10moov" + HDLR_SOUN);

            assertThat(AssistantFileType.de("audio/mp4", mp4)).contains(AssistantFileType.MP4);
        }

        @Test
        void rechazaUnMp4ConPistaDeVideo() {
            byte[] mp4 = ascii("\0\0\0\30ftypisom\0\0\0\0\0\0\0\10moov" + HDLR_SOUN + HDLR_VIDE);

            assertThat(AssistantFileType.de("audio/mp4", mp4)).isEmpty();
        }

        @Test
        void rechazaUnaHeicDeclaradaComoAudio() {
            assertThat(AssistantFileType.de("audio/mp4", ascii("\0\0\0\30ftypheic\0\0\0\0"))).isEmpty();
        }

        @Test
        void rechazaUnArchivoSinCajaFtyp() {
            assertThat(AssistantFileType.de("audio/mp4", ascii("\0\0\0\10moov" + HDLR_SOUN))).isEmpty();
        }
    }

    private static byte[] concatenar(byte[]... partes) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        for (byte[] parte : partes) {
            salida.writeBytes(parte);
        }
        return salida.toByteArray();
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
