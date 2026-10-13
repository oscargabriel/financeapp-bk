package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/** El cuerpo del refresco y del logout. Un refresh ausente es un 400, como un campo del login. */
public record RefreshTokenRequest(

        @NotBlank(message = "El refreshToken es obligatorio")
        String refreshToken) {
}
