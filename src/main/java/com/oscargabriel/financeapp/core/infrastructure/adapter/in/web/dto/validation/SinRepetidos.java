package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Una lista de ids sin el mismo UUID dos veces (FIN-68), comparados ya interpretados: en mayusculas y en minusculas
 * son el mismo. Los que no son UUID no cuentan; su error ya sale sobre su posicion.
 */
@Retention(RUNTIME)
@Target(FIELD)
@Constraint(validatedBy = SinRepetidos.Validador.class)
public @interface SinRepetidos {

    String message() default "Cada id va una sola vez";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<SinRepetidos, List<String>> {

        private static final Pattern UUID_VALIDO = Pattern.compile(Formatos.UUID);

        @Override
        public boolean isValid(List<String> ids, ConstraintValidatorContext context) {
            if (ids == null) {
                return true;
            }
            List<UUID> validos = ids.stream()
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank() && UUID_VALIDO.matcher(id).matches())
                    .map(id -> UUID.fromString(id.trim()))
                    .toList();
            return validos.stream().distinct().count() == validos.size();
        }
    }
}
