package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CreateTransactionRequest;

/**
 * Cuenta destino y categoria dependen del tipo: una transferencia lleva destino distinto del origen y
 * no lleva categoria; un gasto o un ingreso, al reves, y tampoco monto de destino. Cada error sale sobre su propio campo. Que las
 * cuentas y la categoria existan y sean del usuario lo decide el caso de uso.
 */
@Retention(RUNTIME)
@Target(TYPE)
@Constraint(validatedBy = ReglasDeTransferencia.Validador.class)
public @interface ReglasDeTransferencia {

    String message() default "La cuenta destino o la categoria no corresponden al tipo de movimiento";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<ReglasDeTransferencia, CreateTransactionRequest> {

        @Override
        public boolean isValid(CreateTransactionRequest movimiento, ConstraintValidatorContext context) {
            TransactionType tipo = tipo(movimiento.type());
            // Sin un tipo valido no se sabe si categoria o destino sobran: ya hay un error en type.
            if (tipo == null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            return tipo == TransactionType.TRANSFER
                    ? transferencia(movimiento, context)
                    : gastoOIngreso(movimiento, context);
        }

        private static boolean transferencia(CreateTransactionRequest movimiento, ConstraintValidatorContext context) {
            boolean valido = true;
            String destino = movimiento.destinationAccountId();
            if (vacio(destino)) {
                valido = error(context, "destinationAccountId", "La cuenta destino es obligatoria");
            } else if (!vacio(movimiento.accountId())
                    && destino.trim().equalsIgnoreCase(movimiento.accountId().trim())) {
                valido = error(context, "destinationAccountId",
                        "La cuenta destino tiene que ser distinta de la de origen");
            }
            if (movimiento.categoryId() != null) {
                valido = error(context, "categoryId", "Una transferencia no lleva categoria");
            }
            return valido;
        }

        private static boolean gastoOIngreso(CreateTransactionRequest movimiento, ConstraintValidatorContext context) {
            boolean valido = true;
            if (movimiento.destinationAccountId() != null) {
                valido = error(context, "destinationAccountId", "Solo una transferencia lleva cuenta destino");
            }
            if (movimiento.destinationAmount() != null) {
                valido = error(context, "destinationAmount", "Solo una transferencia lleva monto de destino");
            }
            if (vacio(movimiento.categoryId())) {
                valido = error(context, "categoryId", "La categoria es obligatoria en un gasto o un ingreso");
            }
            return valido;
        }

        private static boolean error(ConstraintValidatorContext context, String campo, String texto) {
            context.buildConstraintViolationWithTemplate(texto).addPropertyNode(campo).addConstraintViolation();
            return false;
        }

        private static boolean vacio(String valor) {
            return valor == null || valor.isBlank();
        }

        private static TransactionType tipo(String valor) {
            if (vacio(valor)) {
                return null;
            }
            try {
                return TransactionType.valueOf(valor.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
