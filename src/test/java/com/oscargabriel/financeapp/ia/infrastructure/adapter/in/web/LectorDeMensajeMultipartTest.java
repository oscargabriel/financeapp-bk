package com.oscargabriel.financeapp.ia.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePartEvent;
import org.springframework.http.codec.multipart.FormPartEvent;
import org.springframework.http.codec.multipart.PartEvent;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.ia.domain.model.AssistantFile;
import com.oscargabriel.financeapp.ia.domain.model.AssistantFileType;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class LectorDeMensajeMultipartTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};
    private static final byte[] OGG = "OggS\0\2\0\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final MediaType IMAGE_PNG = MediaType.IMAGE_PNG;

    private static Flux<PartEvent> archivo(String nombre, MediaType tipo, byte[] contenido) {
        return archivo(nombre, tipo, Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(contenido)));
    }

    private static Flux<PartEvent> archivo(String nombre, MediaType tipo, Flux<DataBuffer> trozos) {
        return FilePartEvent.create(nombre, "archivo", tipo, trozos).cast(PartEvent.class);
    }

    private static Flux<PartEvent> texto(String valor) {
        return FormPartEvent.create("data", valor).flux().cast(PartEvent.class);
    }

    @SafeVarargs
    private static Flux<PartEvent> partes(Flux<PartEvent>... partes) {
        return Flux.concat(partes);
    }

    private static void esError(Flux<PartEvent> partes, String campo) {
        StepVerifier.create(LectorDeMensajeMultipart.leer(partes))
                .verifyErrorSatisfies(e -> assertThat(e).isInstanceOfSatisfying(BadRequestException.class, bre -> {
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bre.getErrorResponse().getErrors()).singleElement().satisfies(detalle -> {
                        assertThat(detalle.getCode()).isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode());
                        assertThat(detalle.getField()).isEqualTo(campo);
                    });
                }));
    }

    @Nested
    class Validos {

        @Test
        void archivoYTexto() {
            StepVerifier.create(LectorDeMensajeMultipart.leer(partes(archivo("file", IMAGE_PNG, PNG), texto("con la Nequi"))))
                    .assertNext(mensaje -> {
                        assertThat(mensaje.texto()).isEqualTo("con la Nequi");
                        assertThat(mensaje.archivo().tipo()).isEqualTo(AssistantFileType.PNG);
                        assertThat(mensaje.archivo().contenido()).containsExactly(PNG);
                    })
                    .verifyComplete();
        }

        @Test
        void soloElArchivo() {
            StepVerifier.create(LectorDeMensajeMultipart.leer(partes(archivo("file", IMAGE_PNG, PNG))))
                    .assertNext(mensaje -> {
                        assertThat(mensaje.texto()).isNull();
                        assertThat(mensaje.archivo().tipo()).isEqualTo(AssistantFileType.PNG);
                    })
                    .verifyComplete();
        }

        @Test
        void soloElTexto() {
            StepVerifier.create(LectorDeMensajeMultipart.leer(partes(texto("consultar_saldo"))))
                    .assertNext(mensaje -> {
                        assertThat(mensaje.texto()).isEqualTo("consultar_saldo");
                        assertThat(mensaje.archivo()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        void unTextoEnBlancoJuntoAUnArchivoEsAusente() {
            StepVerifier.create(LectorDeMensajeMultipart.leer(partes(texto("   "), archivo("file", IMAGE_PNG, PNG))))
                    .assertNext(mensaje -> assertThat(mensaje.texto()).isNull())
                    .verifyComplete();
        }

        @Test
        void elArchivoLlegaEnVariosTrozos() {
            byte[] contenido = new byte[3000];
            System.arraycopy(PNG, 0, contenido, 0, PNG.length);
            Flux<DataBuffer> trozos = Flux.just(
                    DefaultDataBufferFactory.sharedInstance.wrap(Arrays.copyOfRange(contenido, 0, 1000)),
                    DefaultDataBufferFactory.sharedInstance.wrap(Arrays.copyOfRange(contenido, 1000, 3000)));

            StepVerifier.create(LectorDeMensajeMultipart.leer(archivo("file", IMAGE_PNG, trozos)))
                    .assertNext(mensaje -> assertThat(mensaje.archivo().contenido()).containsExactly(contenido))
                    .verifyComplete();
        }

        @Test
        void unArchivoJustoEnElTope() {
            byte[] contenido = new byte[AssistantFile.TOPE_BYTES];
            System.arraycopy(PNG, 0, contenido, 0, PNG.length);

            StepVerifier.create(LectorDeMensajeMultipart.leer(partes(archivo("file", IMAGE_PNG, contenido))))
                    .assertNext(mensaje -> assertThat(mensaje.archivo().contenido()).hasSize(AssistantFile.TOPE_BYTES))
                    .verifyComplete();
        }
    }

    @Nested
    class DelArchivo {

        @Test
        void video() {
            esError(partes(archivo("file", MediaType.parseMediaType("video/mp4"),
                    "\0\0\0\30ftypisom".getBytes(StandardCharsets.ISO_8859_1))), "file");
        }

        @Test
        void otroTipo() {
            esError(partes(archivo("file", MediaType.APPLICATION_PDF, "%PDF-1.4".getBytes(StandardCharsets.US_ASCII))),
                    "file");
        }

        @Test
        void contenidoQueNoEsDelTipoDeclarado() {
            esError(partes(archivo("file", MediaType.IMAGE_JPEG, OGG)), "file");
        }

        @Test
        void conElTipoGenerico() {
            esError(partes(archivo("file", MediaType.APPLICATION_OCTET_STREAM, PNG)), "file");
        }

        @Test
        void vacio() {
            esError(partes(archivo("file", IMAGE_PNG, new byte[0])), "file");
        }

        @Test
        void unByteSobreElTope() {
            esError(partes(archivo("file", IMAGE_PNG, new byte[AssistantFile.TOPE_BYTES + 1])), "file");
        }

        /** Corta al pasar el tope: no sigue pidiendo trozos de un archivo que ya se rechazo. */
        @Test
        void dejaDeLeerAlPasarElTope() {
            AtomicInteger pedidos = new AtomicInteger();
            Flux<DataBuffer> infinito = Flux.generate(sink -> {
                pedidos.incrementAndGet();
                sink.next(DefaultDataBufferFactory.sharedInstance.wrap(new byte[1024 * 1024]));
            });

            esError(archivo("file", IMAGE_PNG, infinito), "file");
            assertThat(pedidos.get()).isLessThan(10);
        }

        @Test
        void dosArchivos() {
            esError(partes(archivo("file", IMAGE_PNG, PNG), archivo("file", IMAGE_PNG, PNG)), "file");
        }
    }

    @Nested
    class DelTexto {

        @Test
        void niTextoNiArchivo() {
            esError(partes(texto("   ")), "data");
        }

        @Test
        void sinPartes() {
            esError(Flux.empty(), "data");
        }

        @Test
        void textoSobreElTopeJuntoAUnArchivo() {
            esError(partes(archivo("file", IMAGE_PNG, PNG), texto("a".repeat(1001))), "data");
        }

        @Test
        void textoJustoEnElTope() {
            StepVerifier.create(LectorDeMensajeMultipart.leer(partes(texto("a".repeat(1000)))))
                    .assertNext(mensaje -> assertThat(mensaje.texto()).hasSize(1000))
                    .verifyComplete();
        }

        @Test
        void dosTextos() {
            esError(partes(texto("uno"), texto("dos")), "data");
        }

        @Test
        void parteDesconocida() {
            esError(partes(archivo("foto", IMAGE_PNG, PNG)), "foto");
        }
    }
}
