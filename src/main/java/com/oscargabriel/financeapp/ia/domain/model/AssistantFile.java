package com.oscargabriel.financeapp.ia.domain.model;

import java.util.Objects;

/**
 * Un audio o una imagen ya validados: solo existe con un tipo reconocido (design.md de FIN-57, decision 3). Vive en
 * memoria mientras dura la peticion; nunca se guarda ni se loguea.
 */
public record AssistantFile(AssistantFileType tipo, byte[] contenido) {

    public static final int TOPE_BYTES = 5 * 1024 * 1024;

    public AssistantFile {
        Objects.requireNonNull(tipo, "tipo");
        if (contenido == null || contenido.length == 0 || contenido.length > TOPE_BYTES) {
            throw new IllegalArgumentException("El archivo tiene que tener entre 1 byte y " + TOPE_BYTES + " bytes");
        }
        contenido = contenido.clone();
    }

    @Override
    public byte[] contenido() {
        return contenido.clone();
    }

    public int tamano() {
        return contenido.length;
    }

    @Override
    public String toString() {
        return "AssistantFile[tipo=" + tipo + ", bytes=" + contenido.length + "]";
    }
}
