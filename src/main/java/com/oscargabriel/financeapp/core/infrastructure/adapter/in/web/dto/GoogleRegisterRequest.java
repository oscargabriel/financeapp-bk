package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

import com.oscargabriel.financeapp.core.domain.model.GoogleRegistrationCommand;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation.ZonaIana;

/**
 * Cuerpo del registro con Google (FIN-52). timezone es opcional, como en el alta: el caso de uso la
 * completa con la misma zona por defecto. Que el token sirva lo decide el caso de uso.
 */
public record GoogleRegisterRequest(

        @NotBlank(message = "El token de Google es obligatorio")
        String idToken,

        @ZonaIana
        String timezone) {

    public GoogleRegistrationCommand toCommand() {
        return new GoogleRegistrationCommand(idToken, timezone);
    }
}
