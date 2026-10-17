package com.oscargabriel.financeapp.telegram.domain.model;

/**
 * Un archivo de un mensaje, todavia en Telegram (FIN-61): la foto, la nota de voz o el audio. {@code mime}
 * es el que declara Telegram y {@code tamano} puede ser nulo.
 */
public record ArchivoDeTelegram(String fileId, String mime, Long tamano) {

    /**
     * Copia de AssistantFile.TOPE_BYTES, solo para no descargar lo que Telegram ya declara mas grande: quien
     * rechaza el archivo sigue siendo el asistente (design.md de FIN-61, decision 2).
     */
    public static final int TOPE_ARCHIVO = 5 * 1024 * 1024;

    /** El file_id sirve para descargar el archivo mientras el bot tenga su token: no va a los logs. */
    @Override
    public String toString() {
        return "ArchivoDeTelegram[fileId=***, mime=" + mime + ", tamano=" + tamano + "]";
    }
}
