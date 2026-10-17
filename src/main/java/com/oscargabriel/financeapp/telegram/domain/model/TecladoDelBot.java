package com.oscargabriel.financeapp.telegram.domain.model;

import java.util.UUID;

/** El teclado que acompana una respuesta. Como se dibuja en la Bot API lo decide su adapter. */
public sealed interface TecladoDelBot {

    /** Deja el teclado como esta. */
    TecladoDelBot NINGUNO = new Ninguno();

    /** Un boton que comparte el contacto propio del que lo toca. */
    TecladoDelBot PEDIR_CONTACTO = new PedirContacto();

    /** Quita el boton de compartir el contacto. */
    TecladoDelBot QUITAR = new Quitar();

    record Ninguno() implements TecladoDelBot {
    }

    record PedirContacto() implements TecladoDelBot {
    }

    record Quitar() implements TecladoDelBot {
    }

    /**
     * Los botones Confirmar y Descartar de un grupo registrado (FIN-61). Los datos del boton llevan el id
     * del grupo: 46 bytes, bajo los 64 que admite Telegram.
     */
    record ConfirmarODescartar(UUID grupo) implements TecladoDelBot {

        public static final String CONFIRMAR = "confirmar:";

        public static final String DESCARTAR = "descartar:";

        public String datosDeConfirmar() {
            return CONFIRMAR + grupo;
        }

        public String datosDeDescartar() {
            return DESCARTAR + grupo;
        }
    }
}
