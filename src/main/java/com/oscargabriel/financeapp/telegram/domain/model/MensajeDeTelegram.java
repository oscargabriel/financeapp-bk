package com.oscargabriel.financeapp.telegram.domain.model;

import java.util.Optional;

/**
 * Un mensaje que llego al bot: a que chat responder, si es privado, quien lo escribio y el contacto que
 * comparte, si trae uno (FIN-60). {@code remitenteId} y {@code contacto} pueden ser nulos.
 */
public record MensajeDeTelegram(long chatId, boolean privado, Long remitenteId, Contacto contacto) {

    public Optional<Contacto> contactoCompartido() {
        return Optional.ofNullable(contacto);
    }

    /** Un contacto es el propio solo si su user_id es el del que escribe: si no, es de la agenda o reenviado. */
    public boolean esContactoPropio() {
        return contacto != null && contacto.userId() != null && contacto.userId().equals(remitenteId);
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
