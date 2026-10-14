package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.CreateTransactionRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.ShareRequest;

/**
 * Lo que las partes de un elemento cruzan con el resto (FIN-66): un ingreso no se comparte, cada persona va una sola
 * vez, y la suma no supera el total de un gasto o iguala el de una transferencia. Todo sale sobre shares. Con una
 * parte nula o sin monto no hay suma que comparar: su error ya sale sobre esa parte.
 */
@Retention(RUNTIME)
@Target(TYPE)
@Constraint(validatedBy = ReglasDePartes.Validador.class)
public @interface ReglasDePartes {

    String message() default "Las partes no corresponden al movimiento";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<ReglasDePartes, CreateTransactionRequest> {

        @Override
        public boolean isValid(CreateTransactionRequest movimiento, ConstraintValidatorContext context) {
            List<ShareRequest> partes = movimiento.shares();
            TransactionType tipo = Tipos.de(movimiento.type());
            if (partes == null || partes.isEmpty() || tipo == null) {
                return true;
            }
            String error = error(tipo, movimiento.amount(), partes);
            if (error == null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(error).addPropertyNode("shares").addConstraintViolation();
            return false;
        }

        private static String error(TransactionType tipo, BigDecimal total, List<ShareRequest> partes) {
            if (tipo == TransactionType.INCOME) {
                return "Un ingreso no se comparte";
            }
            List<String> afiliaciones = partes.stream().filter(Objects::nonNull).map(ShareRequest::affiliationId)
                    .filter(Objects::nonNull).map(String::trim).toList();
            if (afiliaciones.stream().distinct().count() < afiliaciones.size()) {
                return "Cada persona va una sola vez";
            }
            if (total == null || partes.stream().anyMatch(p -> p == null || p.amount() == null)) {
                return null;
            }
            BigDecimal suma = partes.stream().map(ShareRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (tipo == TransactionType.EXPENSE && suma.compareTo(total) > 0) {
                return "Las partes suman mas que el total del gasto";
            }
            if (tipo == TransactionType.TRANSFER && suma.compareTo(total) != 0) {
                return "Las partes de una transferencia tienen que sumar el total";
            }
            return null;
        }
    }
}
