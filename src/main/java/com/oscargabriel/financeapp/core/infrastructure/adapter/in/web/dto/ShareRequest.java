package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.oscargabriel.financeapp.core.domain.model.ShareCommand;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation.MontoNumeric;

/**
 * Una parte de un movimiento compartido (FIN-66): la persona, por su afiliacion, y lo que le toca, en la moneda
 * en que llega el monto del movimiento. Que la afiliacion exista y este aceptada lo decide el caso de uso, asi que
 * un id mal formado sale alli como "no existe".
 */
public record ShareRequest(

        @NotBlank(message = "La afiliacion es obligatoria")
        String affiliationId,

        @NotNull(message = "El monto de la parte es obligatorio")
        @Positive(message = "El monto de la parte debe ser mayor que cero")
        @MontoNumeric(message = "El monto de la parte admite hasta 4 decimales y menos de 14 digitos enteros")
        BigDecimal amount) {

    public ShareCommand toCommand() {
        return new ShareCommand(affiliationId, amount);
    }
}
