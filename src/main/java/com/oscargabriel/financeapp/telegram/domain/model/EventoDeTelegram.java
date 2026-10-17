package com.oscargabriel.financeapp.telegram.domain.model;

/** Lo que trae un update que el bot atiende: un mensaje o el toque de un boton (FIN-61). */
public sealed interface EventoDeTelegram permits MensajeDeTelegram, BotonDeTelegram {
}
