package com.oscargabriel.financeapp.ia.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AssistantMessageTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    @Nested
    class Archivo {

        @Test
        void admiteHastaElTope() {
            AssistantFile archivo = new AssistantFile(AssistantFileType.JPEG, new byte[AssistantFile.TOPE_BYTES]);

            assertThat(archivo.contenido()).hasSize(5 * 1024 * 1024);
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
            assertThat(mensaje.archivo()).isNull();
        }

        @Test
        void soloArchivo() {
            AssistantFile archivo = new AssistantFile(AssistantFileType.JPEG, JPEG);

            AssistantMessage mensaje = new AssistantMessage(null, archivo);

            assertThat(mensaje.texto()).isNull();
            assertThat(mensaje.archivo()).isSameAs(archivo);
        }

        @Test
        void rechazaUnMensajeSinTextoNiArchivo() {
            assertThatThrownBy(() -> new AssistantMessage(null, null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rechazaUnTextoEnBlanco() {
            AssistantFile archivo = new AssistantFile(AssistantFileType.JPEG, JPEG);

            assertThatThrownBy(() -> new AssistantMessage("  ", archivo)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
