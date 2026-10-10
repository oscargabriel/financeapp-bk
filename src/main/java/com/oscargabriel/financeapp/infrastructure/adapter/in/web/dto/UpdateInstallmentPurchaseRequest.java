package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.GroupScope;
import com.oscargabriel.financeapp.domain.model.UpdateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ValorDeEnum;

/** El parche de PATCH /installment-purchases/{id}: scope siempre, y null como "no cambia" en lo demas. */
public record UpdateInstallmentPurchaseRequest(

        @NotBlank(message = "El alcance es obligatorio: FUTURE o ALL")
        @ValorDeEnum(value = GroupScope.class, message = "El alcance debe ser FUTURE o ALL")
        String scope,

        @Pattern(regexp = Formatos.NO_EN_BLANCO,
                message = "La descripcion no puede ir en blanco: para no cambiarla, omitela")
        @Size(max = 255, message = "La descripcion no puede superar los 255 caracteres")
        String description,

        String categoryId) {

    /** Un parche que solo trae el alcance se rechaza en el controlador. */
    public boolean sinCambios() {
        return description == null && categoryId == null;
    }

    /** Solo se llama con el cuerpo ya validado. */
    public UpdateInstallmentPurchaseCommand toCommand() {
        return new UpdateInstallmentPurchaseCommand(GroupScope.valueOf(CreateRecurrenceRequest.normalizado(scope)),
                description, categoryId);
    }
}