package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.DateTimeException;
import java.time.ZoneId;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Zona horaria IANA que el JDK reconoce. Vacia es valida: el caso de uso pone la de por defecto. */
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Constraint(validatedBy = ZonaIana.Validador.class)
public @interface ZonaIana {

    String message() default "La zona horaria no es una zona IANA conocida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<ZonaIana, String> {

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext context) {
            if (valor == null || valor.isBlank()) {
                return true;
            }
            try {
                ZoneId.of(valor.trim());
                return true;
            } catch (DateTimeException e) {
                return false;
            }
        }
    }
}
