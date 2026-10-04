package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.stream.Stream;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UpdateTransactionCommand;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.FechaConOffset;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.MontoNumeric;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ValorDeEnum;

/**
 * El parche de PATCH /transactions/{id}: todo opcional, y null es "no cambia". Cada campo que viene
 * cumple el formato del alta. Las reglas de la transferencia no van aqui, a diferencia de
 * CreateTransactionRequest: sin el movimiento guardado no se sabe el tipo resultante.
 *
 * notes, currencyCode y destinationAmount no son modificables: si llegan, el codec los ignora.
 */
public record UpdateTransactionRequest(

        @ValorDeEnum(value = TransactionType.class, message = "El tipo debe ser EXPENSE, INCOME o TRANSFER")
        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "El tipo no puede ir en blanco: para no cambiarlo, omitelo")
        String type,

        String accountId,

        String destinationAccountId,

        String categoryId,

        @Positive(message = "El monto debe ser mayor que cero: el signo lo da el tipo")
        @MontoNumeric(message = "El monto admite hasta 4 decimales y menos de 14 digitos enteros")
        BigDecimal amount,

        @Pattern(regexp = Formatos.NO_EN_BLANCO,
                message = "La descripcion no puede ir en blanco: para no cambiarla, omitela")
        @Size(max = 255, message = "La descripcion no puede superar los 255 caracteres")
        String description,

        /** En blanco no es "ahora", como en el alta: en un parche no hay instante que poner por defecto. */
        @FechaConOffset
        @Pattern(regexp = Formatos.NO_EN_BLANCO, message = "La fecha no puede ir en blanco: para no cambiarla, omitela")
        String occurredAt) {

    /** Un parche que no cambia nada se rechaza en el controlador, antes de leer el movimiento. */
    public boolean sinCambios() {
        return Stream.of(type, accountId, destinationAccountId, categoryId, amount, description, occurredAt)
                .allMatch(campo -> campo == null);
    }

    public UpdateTransactionCommand toCommand() {
        return new UpdateTransactionCommand(type, accountId, destinationAccountId, categoryId, amount, description,
                occurredAt);
    }
}
