package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El texto tal como llegaria de Telegram. El tope acota el costo de cada llamada al modelo: un
 * movimiento dictado cabe de sobra (design.md de FA-77, decision 9).
 */
public record AssistantMessageRequest(
        @NotBlank(message = "El mensaje es obligatorio")
        @Size(max = AssistantMessageRequest.TOPE, message = "El mensaje no puede superar los "
                + AssistantMessageRequest.TOPE + " caracteres")
        String data) {

    public static final int TOPE = 1000;
}
