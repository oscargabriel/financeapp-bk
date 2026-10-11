package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.domain.model.InstallmentPlan;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Fecha;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.MontoParaLasCuotas;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.MontoNumeric;

/**
 * El alta y la simulacion de una compra en cuotas (FA-108). Que la tarjeta sea del usuario y tenga corte
 * y pago, que la categoria admita gastos y que la fecha no sea futura en su zona, lo decide el caso de uso.
 */
@MontoParaLasCuotas
public record CreateInstallmentPurchaseRequest(

        @NotBlank(message = "La cuenta es obligatoria")
        String accountId,

        @NotBlank(message = "La categoria es obligatoria")
        String categoryId,

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor que cero")
        @MontoNumeric(message = "El monto admite hasta 4 decimales y menos de 14 digitos enteros")
        BigDecimal amount,

        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 255, message = "La descripcion no puede superar los 255 caracteres")
        String description,

        @NotBlank(message = "La fecha de compra es obligatoria")
        @Fecha
        String purchaseDate,

        @NotNull(message = "El numero de cuotas es obligatorio")
        @Min(value = 1, message = "Las cuotas van de 1 a " + InstallmentPlan.MAXIMO)
        @Max(value = InstallmentPlan.MAXIMO, message = "Las cuotas van de 1 a " + InstallmentPlan.MAXIMO)
        Integer installmentCount) {

    /** Solo se llama con el cuerpo ya validado. */
    public CreateInstallmentPurchaseCommand toCommand() {
        return new CreateInstallmentPurchaseCommand(accountId, categoryId, amount, description,
                LocalDate.parse(purchaseDate.trim()), installmentCount);
    }
}