package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.stream.Stream;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.Frequency;
import com.oscargabriel.financeapp.domain.model.GroupScope;
import com.oscargabriel.financeapp.domain.model.UpdateRecurrenceCommand;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.MontoNumeric;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ValorDeEnum;

/**
 * El parche de PATCH /recurrences/{id}: scope siempre, y lo demas opcional, con null como "no cambia".
 * La forma de la regla resultante no va aqui: sin la serie guardada no se sabe la frecuencia final.
 */
public record UpdateRecurrenceRequest(

        @NotBlank(message = "El alcance es obligatorio: FUTURE o ALL")
        @ValorDeEnum(value = GroupScope.class, message = "El alcance debe ser FUTURE o ALL")
        String scope,

        String accountId,

        String categoryId,

        @Positive(message = "El monto debe ser mayor que cero: el signo lo da el tipo")
        @MontoNumeric(message = "El monto admite hasta 4 decimales y menos de 14 digitos enteros")
        BigDecimal amount,

        @Pattern(regexp = Formatos.NO_EN_BLANCO,
                message = "La descripcion no puede ir en blanco: para no cambiarla, omitela")
        @Size(max = 255, message = "La descripcion no puede superar los 255 caracteres")
        String description,

        @ValorDeEnum(value = Frequency.class, message = "La frecuencia debe ser WEEKLY o MONTHLY")
        String frequency,

        @Min(value = 1, message = "El intervalo es al menos 1")
        Integer interval,

        @ValorDeEnum(value = DayOfWeek.class, message = "El dia de la semana debe ser MONDAY a SUNDAY, en ingles")
        String dayOfWeek,

        @Min(value = 1, message = "El dia del mes va de 1 a 31")
        @Max(value = 31, message = "El dia del mes va de 1 a 31")
        Integer dayOfMonth) {

    /** Un parche que solo trae el alcance se rechaza en el controlador, antes de leer la serie. */
    public boolean sinCambios() {
        return Stream.of(accountId, categoryId, amount, description, frequency, interval, dayOfWeek, dayOfMonth)
                .allMatch(campo -> campo == null);
    }

    /** Solo se llama con el cuerpo ya validado. Un texto en blanco de un enum es "no cambia". */
    public UpdateRecurrenceCommand toCommand() {
        return new UpdateRecurrenceCommand(
                GroupScope.valueOf(CreateRecurrenceRequest.normalizado(scope)),
                accountId,
                categoryId,
                amount,
                description,
                vacio(frequency) ? null : Frequency.valueOf(CreateRecurrenceRequest.normalizado(frequency)),
                interval,
                vacio(dayOfWeek) ? null : DayOfWeek.valueOf(CreateRecurrenceRequest.normalizado(dayOfWeek)),
                dayOfMonth);
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
