package com.oscargabriel.financeapp.ia.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AssistantMessageTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final byte[] OGG = {'O', 'g', 'g', 'S'};

    @Nested
    class Archivo {

        @Test
        void admiteHastaElTope() {
            AssistantFile archivo = new AssistantFile(AssistantFileType.JPEG, new byte[AssistantFile.TOPE_BYTES]);

            assertThat(archivo.contenido()).hasSize(5 * 1024 * 1024);
            assertThat(archivo.tamano()).isEqualTo(5 * 1024 * 1024);
        }

        @Test
        void rechazaUnByteSobreElTope() {
            assertThatThrownBy(() -> new AssistantFile(AssistantFileType.JPEG, new byte[AssistantFile.TOPE_BYTES + 1]))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rechazaUnArchivoVacio() {
            assertThatThrownBy(() -> new AssistantFile(AssistantFileType.JPEG, new byte[0]))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rechazaUnArchivoSinTipo() {
            assertThatThrownBy(() -> new AssistantFile(null, JPEG)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void cambiarElArregloDeOrigenNoCambiaElArchivo() {
            byte[] origen = JPEG.clone();
            AssistantFile archivo = new AssistantFile(AssistantFileType.JPEG, origen);

            origen[0] = 0;
            archivo.contenido()[1] = 0;

            assertThat(archivo.contenido()).containsExactly(JPEG);
        }
    }

    @Nested
    class Mensaje {

        @Test
        void soloTexto() {
            AssistantMessage mensaje = AssistantMessage.deTexto("gaste 20 mil");

            assertThat(mensaje.texto()).isEqualTo("gaste 20 mil");
            assertThat(mensaje.archivos()).isEmpty();
        }

        @Test
        void soloArchivo() {
            AssistantFile archivo = new AssistantFile(AssistantFileType.JPEG, JPEG);

            AssistantMessage mensaje = new AssistantMessage(null, List.of(archivo));

            assertThat(mensaje.texto()).isNull();
            assertThat(mensaje.archivos()).containsExactly(archivo);
        }

        @Test
        void variasImagenesYUnAudioEnOrden() {
            AssistantFile imagen = new AssistantFile(AssistantFileType.JPEG, JPEG);
            AssistantFile audio = new AssistantFile(AssistantFileType.OGG, OGG);
            AssistantFile otraImagen = new AssistantFile(AssistantFileType.JPEG, JPEG);

            AssistantMessage mensaje = new AssistantMessage("con la Nequi", List.of(imagen, audio, otraImagen));

            assertThat(mensaje.archivos()).containsExactly(imagen, audio, otraImagen);
        }

        @Test
        void rechazaUnMensajeSinTextoNiArchivo() {
            assertThatThrownBy(() -> new AssistantMessage(null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void unaListaNulaEsUnMensajeSinArchivos() {
            assertThat(new AssistantMessage("hola", null).archivos()).isEmpty();
        }

        @Test
        void rechazaUnTextoEnBlanco() {
            AssistantFile archivo = new AssistantFile(AssistantFileType.JPEG, JPEG);

            assertThatThrownBy(() -> new AssistantMessage("  ", List.of(archivo)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rechazaDosAudios() {
            AssistantFile audio = new AssistantFile(AssistantFileType.OGG, OGG);

            assertThatThrownBy(() -> new AssistantMessage(null, List.of(audio, audio)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void admiteJustoElTopeTotal() {
            AssistantFile cincoMegas = new AssistantFile(AssistantFileType.JPEG, new byte[AssistantFile.TOPE_BYTES]);

            AssistantMessage mensaje = new AssistantMessage(null, List.of(cincoMegas, cincoMegas));

            assertThat(AssistantMessage.TOPE_TOTAL_BYTES).isEqualTo(10 * 1024 * 1024);
            assertThat(mensaje.archivos()).hasSize(2);
        }

        @Test
        void rechazaUnByteSobreElTopeTotal() {
            AssistantFile cincoMegas = new AssistantFile(AssistantFileType.JPEG, new byte[AssistantFile.TOPE_BYTES]);
            AssistantFile unByte = new AssistantFile(AssistantFileType.JPEG, new byte[1]);

            assertThatThrownBy(() -> new AssistantMessage(null, List.of(cincoMegas, cincoMegas, unByte)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void laListaEsUnaCopiaInmodificable() {
            AssistantFile archivo = new AssistantFile(AssistantFileType.JPEG, JPEG);
            List<AssistantFile> origen = new ArrayList<>(List.of(archivo));

            AssistantMessage mensaje = new AssistantMessage(null, origen);
            origen.add(archivo);

            assertThat(mensaje.archivos()).hasSize(1);
            assertThatThrownBy(() -> mensaje.archivos().add(archivo))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
