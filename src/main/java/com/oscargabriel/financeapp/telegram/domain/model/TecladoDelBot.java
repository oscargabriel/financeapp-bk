package com.oscargabriel.financeapp.telegram.domain.model;

/** El teclado que acompana una respuesta. Como se dibuja en la Bot API lo decide su adapter. */
public enum TecladoDelBot {
    /** Deja el teclado como esta. */
    NINGUNO,
    /** Un boton que comparte el contacto propio del que lo toca. */
    PEDIR_CONTACTO,
    /** Quita el boton de compartir el contacto. */
    QUITAR
}
