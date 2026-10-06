package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.stream.Stream;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.UpdateAccountCommand;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.MontoNumeric;

/**
 * El parche de PATCH /accounts/{id}: todo opcional, y null es "no cambia". Cada campo que viene cumple
 * el formato del alta y no puede ir en blanco. Que el cupo y los dias solo valgan en una CREDIT lo
 * decide el caso de uso, porque el tipo no viene en el cuerpo: es el de la cuenta guardada.
 *
 * currentBalance, type e isActive se leen solo para rechazarlos: ignorarlos haria creer al cliente
 * que los cambio.
 */
public record UpdateAccountRequest(

        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "El nombre no puede ir en blanco: para no cambiarlo, omitelo")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        String name,

        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "La moneda no puede ir en blanco: para no cambiarla, omitela")
        @Pattern(regexp = Formatos.CODIGO_MONEDA, message = "La moneda debe ser un codigo de tres letras")
        String currencyCode,

        @MontoNumeric(message = "El saldo inicial admite hasta 4 decimales y menos de 14 digitos enteros")
        BigDecimal initialBalance,

        @Positive(message = "El cupo debe ser mayor que cero")
        @MontoNumeric(message = "El cupo admite hasta 4 decimales y menos de 14 digitos enteros")
        BigDecimal creditLimit,

        @Min(value = 1, message = "El dia de corte debe estar entre 1 y 31")
        @Max(value = 31, message = "El dia de corte debe estar entre 1 y 31")
        Integer statementDay,

        @Min(value = 1, message = "El dia de pago debe estar entre 1 y 31")
        @Max(value = 31, message = "El dia de pago debe estar entre 1 y 31")
        Integer paymentDueDay,

        @Null(message = "El saldo vigente lo calcula el sistema; para corregirlo, cambia initialBalance")
        BigDecimal currentBalance,

        @Null(message = "El tipo de una cuenta no se puede cambiar")
        String type,

        @Null(message = "El estado de la cuenta no se cambia con este endpoint")
        Boolean isActive) {

    /** Un parche que no cambia nada se rechaza en el controlador, antes de leer la cuenta. */
    public boolean sinCambios() {
        return Stream.of(name, currencyCode, initialBalance, creditLimit, statementDay, paymentDueDay)
                .allMatch(campo -> campo == null);
    }

    public UpdateAccountCommand toCommand() {
        return new UpdateAccountCommand(name, currencyCode, initialBalance, creditLimit, statementDay, paymentDueDay);
    }
}
