package com.oscargabriel.financeapp.ia.domain.model;

/**
 * Lo que el usuario le manda al asistente: un texto, un archivo o los dos (FIN-57). texto es null cuando no viene,
 * nunca en blanco: el adapter de entrada decide antes que un blanco junto a un archivo es ausencia.
 */
public record AssistantMessage(String texto, AssistantFile archivo) {

    public AssistantMessage {
        if (texto == null && archivo == null) {
            throw new IllegalArgumentException("El mensaje necesita un texto o un archivo");
        }
        if (texto != null && texto.isBlank()) {
            throw new IllegalArgumentException("El texto del mensaje no puede estar en blanco");
        }
    }

    public static AssistantMessage deTexto(String texto) {
        return new AssistantMessage(texto, null);
    }
}
