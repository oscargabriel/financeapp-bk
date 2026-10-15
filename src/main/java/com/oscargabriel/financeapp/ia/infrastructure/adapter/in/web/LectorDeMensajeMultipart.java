package com.oscargabriel.financeapp.ia.infrastructure.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * Valida el multipart del asistente mientras lo lee (design.md de FIN-57, decision 2). Los PartEvent llegan en trozos y
 * nunca tocan el disco; el archivo se corta apenas pasa del tope, sin leer el resto. Un Flux de PartEvent no pasa por
 * Bean Validation, por eso las reglas de formato viven aqui y no en un record de request. Ningun error lleva el
 * contenido de una parte.
 */
final class LectorDeMensajeMultipart {

    static final String DATA = "data";
    static final String FILE = "file";

    /** UTF-8 usa hasta 4 bytes por caracter: el tope de caracteres se mide despues, como en el JSON. */
    private static final int TOPE_TEXTO_BYTES = AssistantMessageRequest.TOPE * 4;

    private static final String SIN_MENSAJE = "El mensaje es obligatorio: envia un texto, un archivo o los dos";
    private static final String TEXTO_LARGO = "El mensaje no puede superar los " + AssistantMessageRequest.TOPE
            + " caracteres";
    private static final String TIPO_NO_ADMITIDO = "El archivo tiene que ser una imagen JPEG, PNG, WEBP, HEIC o HEIF, "
            + "o un audio OGG, MP3, WAV, AAC o FLAC";
    private static final String ARCHIVO_GRANDE = "El archivo no puede superar los 5 MB";
    private static final String ARCHIVO_VACIO = "El archivo esta vacio";

    private LectorDeMensajeMultipart() {
    }

    static Mono<AssistantMessage> leer(Flux<PartEvent> partes) {
        return Mono.defer(() -> {
            Set<String> vistas = new HashSet<>();
            return partes.windowUntil(PartEvent::isLast)
                    .concatMap(eventos -> eventos.switchOnFirst((primero, todos) -> primero.hasValue()
                            ? parte(primero.get(), todos, vistas)
                            : todos.then(Mono.<Parte>empty())))
                    .collectList()
                    .flatMap(LectorDeMensajeMultipart::mensaje);
        });
    }

    private static Mono<Parte> parte(PartEvent primero, Flux<PartEvent> eventos, Set<String> vistas) {
        String nombre = primero.name();
        if (!DATA.equals(nombre) && !FILE.equals(nombre)) {
            return Mono.error(invalido(nombre == null || nombre.isBlank() ? "request" : nombre,
                    "Campo no admitido: el mensaje solo lleva data y file"));
        }
        if (!vistas.add(nombre)) {
            return Mono.error(invalido(nombre, FILE.equals(nombre)
                    ? "Solo se admite un archivo por mensaje"
                    : "El texto del mensaje va una sola vez"));
        }
        MediaType tipo = primero.headers().getContentType();
        if (primero instanceof FormPartEvent campo) {
            byte[] valor = campo.value().getBytes(StandardCharsets.UTF_8);
            return valor.length > tope(nombre)
                    ? Mono.error(invalido(nombre, demasiadoGrande(nombre)))
                    : Mono.just(new Parte(nombre, tipo, valor));
        }
        return DataBufferUtils.join(eventos.map(PartEvent::content), tope(nombre))
                .map(junto -> new Parte(nombre, tipo, bytes(junto)))
                .defaultIfEmpty(new Parte(nombre, tipo, new byte[0]))
                .onErrorMap(DataBufferLimitException.class, e -> invalido(nombre, demasiadoGrande(nombre)));
    }

    private static Mono<AssistantMessage> mensaje(List<Parte> partes) {
        String texto = partes.stream().filter(p -> DATA.equals(p.nombre()))
                .map(p -> new String(p.contenido(), StandardCharsets.UTF_8))
                .filter(t -> !t.isBlank())
                .findFirst().orElse(null);
        Parte archivo = partes.stream().filter(p -> FILE.equals(p.nombre())).findFirst().orElse(null);
        if (texto != null && texto.length() > AssistantMessageRequest.TOPE) {
            return Mono.error(invalido(DATA, TEXTO_LARGO));
        }
        if (archivo == null) {
            return texto == null
                    ? Mono.error(invalido(DATA, SIN_MENSAJE))
                    : Mono.just(AssistantMessage.deTexto(texto));
        }
        if (archivo.contenido().length == 0) {
            return Mono.error(invalido(FILE, ARCHIVO_VACIO));
        }
        String declarado = archivo.tipo() == null ? null : archivo.tipo().toString();
        return AssistantFileType.de(declarado, archivo.contenido())
                .map(tipo -> Mono.just(new AssistantMessage(texto, new AssistantFile(tipo, archivo.contenido()))))
                .orElseGet(() -> Mono.error(invalido(FILE, TIPO_NO_ADMITIDO)));
    }

    private static int tope(String nombre) {
        return FILE.equals(nombre) ? AssistantFile.TOPE_BYTES : TOPE_TEXTO_BYTES;
    }

    private static String demasiadoGrande(String nombre) {
        return FILE.equals(nombre) ? ARCHIVO_GRANDE : TEXTO_LARGO;
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
}
