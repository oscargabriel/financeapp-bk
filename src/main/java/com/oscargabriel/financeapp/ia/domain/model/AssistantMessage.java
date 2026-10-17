package com.oscargabriel.financeapp.ia.domain.model;

import java.util.List;

/**
 * Lo que el usuario le manda al asistente: un texto, archivos o los dos (FIN-57, FIN-113). texto es null cuando no
 * viene, nunca en blanco: el adapter de entrada decide antes que un blanco junto a un archivo es ausencia. Los archivos
 * van en el orden en que llegaron.
 */
public record AssistantMessage(String texto, List<AssistantFile> archivos) {

    public static final int TOPE_TOTAL_BYTES = 10 * 1024 * 1024;

    public AssistantMessage {
        archivos = archivos == null ? List.of() : List.copyOf(archivos);
        if (texto == null && archivos.isEmpty()) {
            throw new IllegalArgumentException("El mensaje necesita un texto o un archivo");
        }
        if (texto != null && texto.isBlank()) {
            throw new IllegalArgumentException("El texto del mensaje no puede estar en blanco");
        }
        if (archivos.stream().filter(archivo -> archivo.tipo().esAudio()).count() > 1) {
            throw new IllegalArgumentException("El mensaje lleva como mucho un audio");
        }
        if (archivos.stream().mapToLong(AssistantFile::tamano).sum() > TOPE_TOTAL_BYTES) {
            throw new IllegalArgumentException("Los archivos no pueden pasar de " + TOPE_TOTAL_BYTES + " bytes");
        }
    }

    public static AssistantMessage deTexto(String texto) {
        return new AssistantMessage(texto, List.of());
    }
}
