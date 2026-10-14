package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo del login con Google (FIN-52): solo el ID token que entrega Google Identity Services. */
public record GoogleLoginRequest(

        @NotBlank(message = "El token de Google es obligatorio")
        String idToken) {
}
