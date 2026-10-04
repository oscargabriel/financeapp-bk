package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Fecha ISO-8601 con offset. Sin offset la hora es ambigua, y el corte de mes de los reportes depende
 * de la zona. Vacia es valida: si el campo es obligatorio, lo exige un @NotBlank aparte.
 */
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Constraint(validatedBy = FechaConOffset.Validador.class)
public @interface FechaConOffset {

    String message() default "La fecha debe ser ISO-8601 con offset, por ejemplo 2026-09-20T10:15:00-05:00";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<FechaConOffset, String> {

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext context) {
            if (valor == null || valor.isBlank()) {
                return true;
            }
            try {
                OffsetDateTime.parse(valor.trim());
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
    }
}
