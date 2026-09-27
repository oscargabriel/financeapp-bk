package com.oscargabriel.financeapp.domain.model;

/** Credenciales tal como llegan del cliente, antes de validarse. */
public record LoginCommand(String email, String password) {
}
