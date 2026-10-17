package com.oscargabriel.financeapp.ia.infrastructure.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FormPartEvent;
import org.springframework.http.codec.multipart.PartEvent;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.ia.domain.model.AssistantFile;
import com.oscargabriel.financeapp.ia.domain.model.AssistantFileType;
import com.oscargabriel.financeapp.ia.domain.model.AssistantMessage;
import com.oscargabriel.financeapp.ia.infrastructure.adapter.in.web.dto.AssistantMessageRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Valida el multipart del asistente mientras lo lee (design.md de FIN-57, decision 2, y de FIN-113, decision 4). Los
 * PartEvent llegan en trozos y nunca tocan el disco; cada archivo se corta apenas pasa de su tope o del total, sin leer
 * el resto. Un Flux de PartEvent no pasa por Bean Validation, por eso las reglas de formato viven aqui y no en un record
 * de request. Ningun error lleva el contenido de una parte.
 */
final class LectorDeMensajeMultipart {

    static final String DATA = "data";
    static final String FILE = "file";

    /** UTF-8 usa hasta 4 bytes por caracter: el tope de caracteres se mide despues, como en el JSON. */
    private static final int TOPE_TEXTO_BYTES = AssistantMessageRequest.TOPE * 4;

    private static final String SIN_MENSAJE = "El mensaje es obligatorio: envia un texto, un archivo o los dos";
    private static final String TEXTO_LARGO = "El mensaje no puede superar los " + AssistantMessageRequest.TOPE
            + " caracteres";
    private static final String TEXTO_REPETIDO = "El texto del mensaje va una sola vez";
    private static final String TIPO_NO_ADMITIDO = "El archivo tiene que ser una imagen JPEG, PNG, WEBP, HEIC o HEIF, "
            + "o un audio OGG, MP3, WAV, AAC, FLAC, WEBM o MP4";
    private static final String ARCHIVO_GRANDE = "El archivo no puede superar los 5 MB";
    private static final String TOTAL_GRANDE = "Los archivos no pueden superar los 10 MB entre todos";
    private static final String ARCHIVO_VACIO = "El archivo esta vacio";
    private static final String AUDIO_REPETIDO = "El mensaje lleva como mucho un audio";

    private LectorDeMensajeMultipart() {
    }

    static Mono<AssistantMessage> leer(Flux<PartEvent> partes, int maxImagenes) {
        return Mono.defer(() -> {
            Lectura lectura = new Lectura(maxImagenes);
            return partes.windowUntil(PartEvent::isLast)
                    .concatMap(eventos -> eventos.switchOnFirst((primero, todos) -> primero.hasValue()
                            ? parte(primero.get(), todos, lectura)
                            : todos.then(Mono.<Parte>empty())))
                    .collectList()
                    .flatMap(leidas -> mensaje(leidas, maxImagenes));
        });
    }

    private static Mono<Parte> parte(PartEvent primero, Flux<PartEvent> eventos, Lectura lectura) {
        String nombre = primero.name();
        if (!DATA.equals(nombre) && !FILE.equals(nombre)) {
            return Mono.error(invalido(nombre == null || nombre.isBlank() ? "request" : nombre,
                    "Campo no admitido: el mensaje solo lleva data y file"));
        }
        if (DATA.equals(nombre) && lectura.conTexto) {
            return Mono.error(invalido(DATA, TEXTO_REPETIDO));
        }
        if (DATA.equals(nombre)) {
            lectura.conTexto = true;
        } else if (++lectura.archivos > lectura.maxImagenes + 1) {
            return Mono.error(invalido(FILE, demasiados(lectura.maxImagenes)));
        }
        int numero = lectura.archivos;
        int tope = DATA.equals(nombre) ? TOPE_TEXTO_BYTES
                : Math.min(AssistantFile.TOPE_BYTES, AssistantMessage.TOPE_TOTAL_BYTES - lectura.bytes);
        String porEncima = DATA.equals(nombre) ? TEXTO_LARGO
                : tope == AssistantFile.TOPE_BYTES ? delArchivo(numero, numero > 1, ARCHIVO_GRANDE) : TOTAL_GRANDE;
        MediaType tipo = primero.headers().getContentType();
        Mono<byte[]> contenido = primero instanceof FormPartEvent campo
                ? Mono.just(campo.value().getBytes(StandardCharsets.UTF_8))
                : DataBufferUtils.join(eventos.map(PartEvent::content), tope)
                        .map(LectorDeMensajeMultipart::bytes)
                        .defaultIfEmpty(new byte[0]);
        return contenido
                .onErrorMap(DataBufferLimitException.class, e -> invalido(nombre, porEncima))
                .flatMap(valor -> valor.length > tope
                        ? Mono.error(invalido(nombre, porEncima))
                        : Mono.just(new Parte(nombre, tipo, valor)))
                .doOnNext(leida -> lectura.bytes += FILE.equals(nombre) ? leida.contenido().length : 0);
    }

    private static Mono<AssistantMessage> mensaje(List<Parte> partes, int maxImagenes) {
        String texto = partes.stream().filter(p -> DATA.equals(p.nombre()))
                .map(p -> new String(p.contenido(), StandardCharsets.UTF_8))
                .filter(t -> !t.isBlank())
                .findFirst().orElse(null);
        List<Parte> archivos = partes.stream().filter(p -> FILE.equals(p.nombre())).toList();
        if (texto != null && texto.length() > AssistantMessageRequest.TOPE) {
            return Mono.error(invalido(DATA, TEXTO_LARGO));
        }
        if (archivos.isEmpty() && texto == null) {
            return Mono.error(invalido(DATA, SIN_MENSAJE));
        }
        List<AssistantFile> validos = new ArrayList<>();
        for (int i = 0; i < archivos.size(); i++) {
            Optional<AssistantFile> archivo = archivo(archivos.get(i));
            if (archivo.isEmpty()) {
                String motivo = archivos.get(i).contenido().length == 0 ? ARCHIVO_VACIO : TIPO_NO_ADMITIDO;
                return Mono.error(invalido(FILE, delArchivo(i + 1, archivos.size() > 1, motivo)));
            }
            validos.add(archivo.get());
        }
        long audios = validos.stream().filter(archivo -> archivo.tipo().esAudio()).count();
        if (audios > 1) {
            return Mono.error(invalido(FILE, AUDIO_REPETIDO));
        }
        if (validos.size() - audios > maxImagenes) {
            return Mono.error(invalido(FILE, demasiados(maxImagenes)));
        }
        return Mono.just(new AssistantMessage(texto, validos));
    }

    private static Optional<AssistantFile> archivo(Parte parte) {
        if (parte.contenido().length == 0) {
            return Optional.empty();
        }
        String declarado = parte.tipo() == null ? null : parte.tipo().toString();
        return AssistantFileType.de(declarado, parte.contenido())
                .map(tipo -> new AssistantFile(tipo, parte.contenido()));
    }

    /** Con un solo archivo el mensaje queda como antes de FIN-113; con varios dice cual fallo, contado desde 1. */
    private static String delArchivo(int numero, boolean varios, String motivo) {
        return varios
                ? "Archivo " + numero + ": " + Character.toLowerCase(motivo.charAt(0)) + motivo.substring(1)
                : motivo;
    }

    private static String demasiados(int maxImagenes) {
        return "El mensaje lleva como mucho " + maxImagenes + (maxImagenes == 1 ? " imagen" : " imagenes")
                + " y un audio";
    }

    private static byte[] bytes(DataBuffer junto) {
        try {
            byte[] contenido = new byte[junto.readableByteCount()];
            junto.read(contenido);
            return contenido;
        } finally {
            DataBufferUtils.release(junto);
        }
    }

    private static BadRequestException invalido(String campo, String descripcion) {
        return new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, descripcion, campo);
    }

    private record Parte(String nombre, MediaType tipo, byte[] contenido) {
    }

    /** Lo que va leido: las partes llegan en orden por el concatMap, asi que no hay acceso concurrente. */
    private static final class Lectura {

        private final int maxImagenes;
        private boolean conTexto;
        private int archivos;
        private int bytes;

        private Lectura(int maxImagenes) {
            this.maxImagenes = maxImagenes;
        }
    }
}
