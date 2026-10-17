package com.oscargabriel.financeapp.telegram.domain.exceptions;

import java.io.Serial;

/**
 * El grupo de un boton ya no tiene pendientes del usuario del chat: se confirmo, se descarto, se resolvio en la web o
 * es de otro usuario (design.md de FIN-61, decision 5).
 */
public class GrupoYaNoPendienteException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public GrupoYaNoPendienteException(Throwable causa) {
        super("El grupo ya no tiene pendientes", causa);
    }
}
