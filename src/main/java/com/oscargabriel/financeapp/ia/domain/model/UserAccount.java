package com.oscargabriel.financeapp.ia.domain.model;

import java.util.UUID;

/** Una cuenta activa del usuario: lo unico que el asistente necesita para resolverla por nombre. */
public record UserAccount(UUID id, String name, String currencyCode) {
}
