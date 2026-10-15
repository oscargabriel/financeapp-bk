package com.oscargabriel.financeapp.core.domain.model;

/**
 * La primera contrasena en claro y el ID token de Google que confirma la identidad en ese momento
 * (FIN-53). La contrasena solo sale de aqui convertida en hash.
 */
public record FirstPasswordCommand(String idToken, String newPassword) {
}
