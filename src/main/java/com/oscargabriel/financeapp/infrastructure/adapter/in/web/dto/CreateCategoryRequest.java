package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateCategoryCommand;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ValorDeEnum;

/**
 * Cuerpo del alta de una categoria. Aqui va el formato; que el nombre no lo use otra categoria viva
 * del usuario lo decide el indice unico de la base.
 */
public record CreateCategoryRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 60, message = "El nombre no puede superar los 60 caracteres")
        String name,

        @NotBlank(message = "appliesTo es obligatorio")
        @ValorDeEnum(value = CategoryScope.class, message = "appliesTo debe ser EXPENSE, INCOME o BOTH")
        String appliesTo,

        @NotBlank(message = "El icono es obligatorio")
        @Size(max = 40, message = "El icono no puede superar los 40 caracteres")
        String icon,

        @NotBlank(message = "El color es obligatorio")
        @Pattern(regexp = Formatos.COLOR_HEX, message = "El color debe tener la forma #RRGGBB")
        String color) {

    public CreateCategoryCommand toCommand() {
        return new CreateCategoryCommand(name, appliesTo, icon, color);
    }
}
