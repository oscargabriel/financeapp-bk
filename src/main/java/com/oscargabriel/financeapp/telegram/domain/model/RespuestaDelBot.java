package com.oscargabriel.financeapp.telegram.domain.model;

/** Lo que el bot contesta: el texto y que hace con el teclado del chat (FIN-60). */
public record RespuestaDelBot(String texto, TecladoDelBot teclado) {
}
