package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.CreateAccountCommand;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.CamposDeCredito;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.MontoNumeric;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ValorDeEnum;

/**
 * Cuerpo del alta de una cuenta. currentBalance se lee solo para rechazarlo: lo calcula el sistema.
 *
 * Aqui va el formato; que la moneda este activa en el catalogo lo decide el caso de uso.
 */
@CamposDeCredito
public record CreateAccountRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        String name,

        @NotBlank(message = "El tipo es obligatorio")
        @ValorDeEnum(value = AccountType.class,
                message = "El tipo debe ser uno de CASH, DEBIT, CREDIT, SAVINGS, INVESTMENT u OTHER")
        String type,

        @NotBlank(message = "La moneda es obligatoria")
        @Pattern(regexp = Formatos.CODIGO_MONEDA, message = "La moneda debe ser un codigo de tres letras")
        String currencyCode,

        @MontoNumeric(message = "El saldo inicial admite hasta 4 decimales y menos de 14 digitos enteros")
        BigDecimal initialBalance,

        BigDecimal creditLimit,

        Integer statementDay,

        Integer paymentDueDay,

        BigDecimal monthlyInterestRate,

        @Null(message = "El saldo vigente lo calcula el sistema; envia initialBalance")
        BigDecimal currentBalance) {

    public CreateAccountCommand toCommand() {
        return new CreateAccountCommand(name, type, currencyCode, initialBalance, creditLimit,
                statementDay, paymentDueDay, monthlyInterestRate, currentBalance);
    }
}
