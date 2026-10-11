package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Un dia sin hora, YYYY-MM-DD: las fechas de una serie son dias en la zona del usuario, no instantes.
 * Vacia es valida: si el campo es obligatorio, lo exige un @NotBlank aparte.
 */
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Constraint(validatedBy = Fecha.Validador.class)
public @interface Fecha {

    String message() default "La fecha debe ser YYYY-MM-DD, por ejemplo 2026-10-15";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Fecha, String> {

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext context) {
            return valor == null || valor.isBlank() || leer(valor) != null;
        }

        /** El dia, o null si el texto no es una fecha. */
        public static LocalDate leer(String valor) {
            try {
                return LocalDate.parse(valor.trim());
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }
}
