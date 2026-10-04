package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Texto que nombra una constante del enum, sin distinguir mayusculas y con los espacios del borde
 * tolerados. Vacio es valido: de exigir el campo se encarga @NotBlank.
 */
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Constraint(validatedBy = ValorDeEnum.Validador.class)
public @interface ValorDeEnum {

    Class<? extends Enum<?>> value();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<ValorDeEnum, String> {

        private Enum<?>[] constantes;

        @Override
        public void initialize(ValorDeEnum anotacion) {
            this.constantes = anotacion.value().getEnumConstants();
        }

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext context) {
            if (valor == null || valor.isBlank()) {
                return true;
            }
            String nombre = valor.trim();
            for (Enum<?> constante : constantes) {
                if (constante.name().equalsIgnoreCase(nombre)) {
                    return true;
                }
            }
            return false;
        }
    }
}
