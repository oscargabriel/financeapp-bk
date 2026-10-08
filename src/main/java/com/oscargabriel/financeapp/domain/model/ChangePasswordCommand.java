package com.oscargabriel.financeapp.domain.model;

/** Las dos contrasenas en claro, como llegan. La nueva solo sale de aqui convertida en hash. */
public record ChangePasswordCommand(String currentPassword, String newPassword) {
}
