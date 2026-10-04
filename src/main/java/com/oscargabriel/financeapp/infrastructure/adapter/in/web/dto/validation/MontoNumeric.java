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

/**
 * Monto que cabe en NUMERIC(18,4): hasta 4 decimales y menos de 14 digitos enteros. Pasarse daria un
 * error de la base, no un 400. No es @Digits porque los ceros a la derecha no cuentan: 1.50000 cabe.
 */
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Constraint(validatedBy = MontoNumeric.Validador.class)
public @interface MontoNumeric {

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<MontoNumeric, BigDecimal> {

        @Override
        public boolean isValid(BigDecimal valor, ConstraintValidatorContext context) {
            return valor == null || Montos.cabe(valor);
        }
    }
}
