package com.oscargabriel.financeapp.telegram.domain.model;

import java.time.ZoneId;
import java.util.UUID;

/**
 * El usuario de FinanceApp al que esta vinculado un chat, con lo que el bot necesita para saludarlo y para
 * mostrarle las fechas en su zona (FIN-61).
 */
public record UsuarioDelBot(UUID id, String nombre, ZoneId zona) {
}
