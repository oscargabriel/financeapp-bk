package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.domain.model.CreateRecurrenceCommand;
import com.oscargabriel.financeapp.domain.model.Frequency;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.Fecha;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.MontoNumeric;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ReglasDeSerie;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation.ValorDeEnum;

/**
 * El alta de una serie (FA-107). Las reglas que cruzan campos —el tipo, el dia segun la frecuencia,
 * el fin— las aplica @ReglasDeSerie, cada error sobre su campo. Que la cuenta y la categoria sean del
 * usuario, y el tope de ocurrencias, los decide el caso de uso.
 */
@ReglasDeSerie
public record CreateRecurrenceRequest(

        @NotBlank(message = "El tipo es obligatorio")
        @ValorDeEnum(value = TransactionType.class, message = "El tipo debe ser EXPENSE o INCOME")
        String type,

        @NotBlank(message = "La cuenta es obligatoria")
        String accountId,

        @NotBlank(message = "La categoria es obligatoria")
        String categoryId,

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor que cero: el signo lo da el tipo")
        @MontoNumeric(message = "El monto admite hasta 4 decimales y menos de 14 digitos enteros")
        BigDecimal amount,

        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 255, message = "La descripcion no puede superar los 255 caracteres")
        String description,

        @NotBlank(message = "La frecuencia es obligatoria")
        @ValorDeEnum(value = Frequency.class, message = "La frecuencia debe ser WEEKLY o MONTHLY")
        String frequency,

        /** Opcional: sin el, cada semana o cada mes. */
        Integer interval,

        @ValorDeEnum(value = DayOfWeek.class, message = "El dia de la semana debe ser MONDAY a SUNDAY, en ingles")
        String dayOfWeek,

        Integer dayOfMonth,

        @NotBlank(message = "La fecha de inicio es obligatoria")
        @Fecha
        String startDate,

        @Fecha
        String endDate,

        @Min(value = 1, message = "Las repeticiones van de 1 a 500")
        @Max(value = 500, message = "Las repeticiones van de 1 a 500")
        Integer occurrences) {

    /** Sin interval, cada semana o cada mes. */
    public int intervalOrDefault() {
        return interval == null ? 1 : interval;
    }

    /** Solo se llama con el cuerpo ya validado: cada texto se puede convertir. */
    public CreateRecurrenceCommand toCommand() {
        return new CreateRecurrenceCommand(
                TransactionType.valueOf(normalizado(type)),
                accountId,
                categoryId,
                amount,
                description,
                Frequency.valueOf(normalizado(frequency)),
                intervalOrDefault(),
                dayOfWeek == null ? null : DayOfWeek.valueOf(normalizado(dayOfWeek)),
                dayOfMonth,
                LocalDate.parse(startDate.trim()),
                endDate == null || endDate.isBlank() ? null : LocalDate.parse(endDate.trim()),
                occurrences);
    }

    static String normalizado(String valor) {
        return valor.trim().toUpperCase(Locale.ROOT);
    }
}
