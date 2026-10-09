package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.math.BigDecimal;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Tasa de interes mensual que cumple Tasas. No es @Digits por la misma razon que MontoNumeric. */
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Constraint(validatedBy = TasaMensual.Validador.class)
public @interface TasaMensual {

    String message() default Tasas.FUERA_DE_RANGO;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<TasaMensual, BigDecimal> {

        @Override
        public boolean isValid(BigDecimal valor, ConstraintValidatorContext context) {
            return valor == null || Tasas.cabe(valor);
        }
    }
}
