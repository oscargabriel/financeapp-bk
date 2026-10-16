package com.oscargabriel.financeapp.telegram.domain.model;

import java.util.UUID;

/** El usuario de FinanceApp al que esta vinculado un chat, con lo que el bot necesita para saludarlo. */
public record UsuarioDelBot(UUID id, String nombre) {
}
