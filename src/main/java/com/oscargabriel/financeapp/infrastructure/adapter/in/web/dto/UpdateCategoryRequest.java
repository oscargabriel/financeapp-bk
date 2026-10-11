package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.util.stream.Stream;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.UpdateCategoryCommand;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ValorDeEnum;

/**
 * El parche de PATCH /categories/{id}: todo opcional, y null es "no cambia". Cada campo que viene
 * cumple el formato del alta y no puede ir en blanco: a diferencia del alta, un icono o un color
 * vacios no se guardan como null, porque el parche no vacia campos.
 */
public record UpdateCategoryRequest(

        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "El nombre no puede ir en blanco: para no cambiarlo, omitelo")
        @Size(max = 60, message = "El nombre no puede superar los 60 caracteres")
        String name,

        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "appliesTo no puede ir en blanco: para no cambiarlo, omitelo")
        @ValorDeEnum(value = CategoryScope.class, message = "appliesTo debe ser EXPENSE, INCOME o BOTH")
        String appliesTo,

        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "El icono no puede ir en blanco: para no cambiarlo, omitelo")
        @Size(max = 40, message = "El icono no puede superar los 40 caracteres")
        String icon,

        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "El color no puede ir en blanco: para no cambiarlo, omitelo")
        @Pattern(regexp = Formatos.COLOR_HEX, message = "El color debe tener la forma #RRGGBB")
        String color) {

    /** Un parche que no cambia nada se rechaza en el controlador, antes de leer la categoria. */
    public boolean sinCambios() {
        return Stream.of(name, appliesTo, icon, color).allMatch(campo -> campo == null);
    }

    public UpdateCategoryCommand toCommand() {
        return new UpdateCategoryCommand(name, appliesTo, icon, color);
    }
}
