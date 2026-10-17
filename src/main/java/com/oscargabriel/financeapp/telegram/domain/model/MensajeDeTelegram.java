package com.oscargabriel.financeapp.telegram.domain.model;

import java.util.Optional;

/**
 * Un mensaje que llego al bot: a que chat responder, si es privado, quien lo escribio y el contacto que
 * comparte, si trae uno (FIN-60). {@code texto} es el text o el pie de foto, y {@code archivo} la foto, la
 * nota de voz o el audio (FIN-61). {@code remitenteId}, {@code contacto}, {@code texto} y {@code archivo}
 * pueden ser nulos.
 */
public record MensajeDeTelegram(long chatId, boolean privado, Long remitenteId, Contacto contacto, String texto,
        ArchivoDeTelegram archivo) implements EventoDeTelegram {

    private static final String START = "/start";

    public Optional<Contacto> contactoCompartido() {
        return Optional.ofNullable(contacto);
    }

    /** Un contacto es el propio solo si su user_id es el del que escribe: si no, es de la agenda o reenviado. */
    public boolean esContactoPropio() {
        return contacto != null && contacto.userId() != null && contacto.userId().equals(remitenteId);
    }

    /** Lo primero que manda todo chat nuevo, con o sin el parametro de un enlace profundo. */
    public boolean esStart() {
        if (texto == null) {
            return false;
        }
        String limpio = texto.strip();
        return limpio.equals(START) || limpio.startsWith(START + " ");
    }

    /** Un video, un documento, un sticker o cualquier tipo nuevo de Telegram: nada que el bot entienda. */
    public boolean sinContenido() {
        return (texto == null || texto.isBlank()) && archivo == null && contacto == null;
    }

    /** El telefono tal como lo manda Telegram, a veces sin +. Puede ser nulo. */
    public record Contacto(Long userId, String telefono) {

        /** Los telefonos no van a los logs: ni un log descuidado del mensaje los deja ahi. */
        @Override
        public String toString() {
            return "Contacto[userId=" + userId + ", telefono=***]";
        }
    }
}
