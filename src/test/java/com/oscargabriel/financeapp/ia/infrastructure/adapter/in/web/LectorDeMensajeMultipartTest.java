package com.oscargabriel.financeapp.ia.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
import com.oscargabriel.financeapp.ia.domain.model.AssistantMessage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LectorDeMensajeMultipartTest {

    private static final int MAX_IMAGENES = 3;
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};
    private static final byte[] OGG = "OggS\0\2\0\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final MediaType IMAGE_PNG = MediaType.IMAGE_PNG;
    private static final MediaType AUDIO_OGG = MediaType.parseMediaType("audio/ogg");

    private static Mono<AssistantMessage> leer(Flux<PartEvent> partes) {
        return LectorDeMensajeMultipart.leer(partes, MAX_IMAGENES);
    }

    private static Flux<PartEvent> archivo(String nombre, MediaType tipo, byte[] contenido) {
        return archivo(nombre, tipo, Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(contenido)));
    }

    private static Flux<PartEvent> archivo(String nombre, MediaType tipo, Flux<DataBuffer> trozos) {
        return FilePartEvent.create(nombre, "archivo", tipo, trozos).cast(PartEvent.class);
    }

    private static Flux<PartEvent> imagen() {
        return archivo("file", IMAGE_PNG, PNG);
    }

    private static Flux<PartEvent> imagen(int tamano) {
        byte[] contenido = new byte[tamano];
        System.arraycopy(PNG, 0, contenido, 0, PNG.length);
        return archivo("file", IMAGE_PNG, contenido);
    }

    private static Flux<PartEvent> audio() {
        return archivo("file", AUDIO_OGG, OGG);
    }

    private static Flux<PartEvent> imagenes(int cuantas) {
        return Flux.range(0, cuantas).concatMap(i -> imagen());
    }

    private static Flux<PartEvent> texto(String valor) {
        return FormPartEvent.create("data", valor).flux().cast(PartEvent.class);
    }

    @SafeVarargs
    private static Flux<PartEvent> partes(Flux<PartEvent>... partes) {
        return Flux.concat(partes);
    }

    private static void esError(Flux<PartEvent> partes, String campo) {
        esError(leer(partes), campo, descripcion -> {
        });
    }

    private static void esError(Mono<AssistantMessage> lectura, String campo, Consumer<String> descripcion) {
        StepVerifier.create(lectura)
                .verifyErrorSatisfies(e -> assertThat(e).isInstanceOfSatisfying(BadRequestException.class, bre -> {
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bre.getErrorResponse().getErrors()).singleElement().satisfies(detalle -> {
                        assertThat(detalle.getCode()).isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode());
                        assertThat(detalle.getField()).isEqualTo(campo);
                        descripcion.accept(detalle.getDescription());
                    });
                }));
    }

    @Nested
    class Validos {

        @Test
        void archivoYTexto() {
            StepVerifier.create(leer(partes(imagen(), texto("con la Nequi"))))
                    .assertNext(mensaje -> {
                        assertThat(mensaje.texto()).isEqualTo("con la Nequi");
                        assertThat(mensaje.archivos()).singleElement().satisfies(archivo -> {
                            assertThat(archivo.tipo()).isEqualTo(AssistantFileType.PNG);
                            assertThat(archivo.contenido()).containsExactly(PNG);
                        });
                    })
                    .verifyComplete();
        }

        @Test
        void soloElArchivo() {
            StepVerifier.create(leer(partes(imagen())))
                    .assertNext(mensaje -> {
                        assertThat(mensaje.texto()).isNull();
                        assertThat(mensaje.archivos()).extracting(AssistantFile::tipo)
                                .containsExactly(AssistantFileType.PNG);
                    })
                    .verifyComplete();
        }

        @Test
        void soloElTexto() {
            StepVerifier.create(leer(partes(texto("consultar_saldo"))))
                    .assertNext(mensaje -> {
                        assertThat(mensaje.texto()).isEqualTo("consultar_saldo");
                        assertThat(mensaje.archivos()).isEmpty();
                    })
                    .verifyComplete();
        }

        @Test
        void unTextoEnBlancoJuntoAUnArchivoEsAusente() {
            StepVerifier.create(leer(partes(texto("   "), imagen())))
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

            StepVerifier.create(leer(archivo("file", IMAGE_PNG, trozos)))
                    .assertNext(mensaje -> assertThat(mensaje.archivos().getFirst().contenido()).containsExactly(contenido))
                    .verifyComplete();
        }

        @Test
        void unArchivoJustoEnElTope() {
            StepVerifier.create(leer(partes(imagen(AssistantFile.TOPE_BYTES))))
                    .assertNext(mensaje -> assertThat(mensaje.archivos().getFirst().contenido())
                            .hasSize(AssistantFile.TOPE_BYTES))
                    .verifyComplete();
        }

        @Test
        void nImagenes() {
            StepVerifier.create(leer(imagenes(MAX_IMAGENES)))
                    .assertNext(mensaje -> assertThat(mensaje.archivos()).hasSize(MAX_IMAGENES))
                    .verifyComplete();
        }

        @Test
        void unAudioYNImagenesEnOrden() {
            StepVerifier.create(leer(partes(audio(), imagenes(MAX_IMAGENES), texto("con la Nequi"))))
                    .assertNext(mensaje -> assertThat(mensaje.archivos()).extracting(AssistantFile::tipo)
                            .containsExactly(AssistantFileType.OGG, AssistantFileType.PNG, AssistantFileType.PNG,
                                    AssistantFileType.PNG))
                    .verifyComplete();
        }

        @Test
        void dosDeCincoMegasJustoEnElTotal() {
            StepVerifier.create(leer(partes(imagen(AssistantFile.TOPE_BYTES), imagen(AssistantFile.TOPE_BYTES))))
                    .assertNext(mensaje -> assertThat(mensaje.archivos()).extracting(AssistantFile::tamano)
                            .containsExactly(AssistantFile.TOPE_BYTES, AssistantFile.TOPE_BYTES))
                    .verifyComplete();
        }

        @Test
        void conMaximoCincoAdmiteCinco() {
            StepVerifier.create(LectorDeMensajeMultipart.leer(imagenes(5), 5))
                    .assertNext(mensaje -> assertThat(mensaje.archivos()).hasSize(5))
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
            esError(leer(partes(archivo("file", MediaType.APPLICATION_PDF,
                    "%PDF-1.4".getBytes(StandardCharsets.US_ASCII)))), "file",
                    descripcion -> assertThat(descripcion).doesNotContain("Archivo"));
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
            esError(partes(imagen(AssistantFile.TOPE_BYTES + 1)), "file");
        }

        /** Corta al pasar el tope: no sigue pidiendo trozos de un archivo que ya se rechazo. */
        @Test
        void dejaDeLeerAlPasarElTope() {
            AtomicInteger pedidos = new AtomicInteger();

            esError(archivo("file", IMAGE_PNG, infinito(pedidos)), "file");
            assertThat(pedidos.get()).isLessThan(10);
        }

        @Test
        void unaImagenDeMas() {
            esError(imagenes(MAX_IMAGENES + 1), "file");
        }

        @Test
        void conMaximoUnoElMensajeVaEnSingular() {
            esError(LectorDeMensajeMultipart.leer(imagenes(2), 1), "file",
                    descripcion -> assertThat(descripcion).contains("1 imagen y un audio"));
        }

        @Test
        void dosAudios() {
            esError(partes(imagen(), audio(), audio()), "file");
        }

        @Test
        void porEncimaDelTotal() {
            int cuatroMegas = 4 * 1024 * 1024;
            esError(leer(partes(imagen(cuatroMegas), imagen(cuatroMegas), imagen(cuatroMegas))), "file",
                    descripcion -> assertThat(descripcion).contains("10 MB"));
        }

        @Test
        void unoNoAdmitidoEntreVariosDiceCual() {
            esError(leer(partes(imagen(), imagen(),
                    archivo("file", MediaType.APPLICATION_PDF, "%PDF-1.4".getBytes(StandardCharsets.US_ASCII)))),
                    "file", descripcion -> assertThat(descripcion).startsWith("Archivo 3: "));
        }

        /** La parte N + 2 ya sobra, sea imagen o audio: se rechaza sin juntar su contenido. */
        @Test
        void noLeeLasPartesQueSobran() {
            AtomicInteger pedidos = new AtomicInteger();

            esError(partes(imagenes(MAX_IMAGENES + 1), archivo("file", IMAGE_PNG, infinito(pedidos)), imagen()),
                    "file");
            assertThat(pedidos.get()).isLessThan(3);
        }

        private static Flux<DataBuffer> infinito(AtomicInteger pedidos) {
            return Flux.generate(sink -> {
                byte[] trozo = new byte[1024 * 1024];
                System.arraycopy(PNG, 0, trozo, 0, PNG.length);
                pedidos.incrementAndGet();
                sink.next(DefaultDataBufferFactory.sharedInstance.wrap(trozo));
            });
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
            esError(partes(imagen(), texto("a".repeat(1001))), "data");
        }

        @Test
        void textoJustoEnElTope() {
            StepVerifier.create(leer(partes(texto("a".repeat(1000)))))
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
