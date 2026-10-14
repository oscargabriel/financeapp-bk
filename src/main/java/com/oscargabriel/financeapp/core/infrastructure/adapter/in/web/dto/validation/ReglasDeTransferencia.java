package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.CreateTransactionRequest;

/**
 * Cuenta destino y categoria dependen del tipo: una transferencia lleva destino distinto del origen y
 * no lleva categoria; un gasto o un ingreso, al reves, y tampoco monto de destino. Cada error sale sobre su propio campo. Que las
 * cuentas y la categoria existan y sean del usuario lo decide el caso de uso. Una transferencia con partes no lleva
 * destino ni monto de destino y si categoria (FIN-66).
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
            TransactionType tipo = Tipos.de(movimiento.type());
            // Sin un tipo valido no se sabe si categoria o destino sobran: ya hay un error en type.
            if (tipo == null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            if (tipo != TransactionType.TRANSFER) {
                return gastoOIngreso(movimiento, context);
            }
            return movimiento.shares() == null || movimiento.shares().isEmpty()
                    ? transferencia(movimiento, context)
                    : transferenciaCompartida(movimiento, context);
        }

        /**
         * Va a otras personas y no a una cuenta propia: en los libros de quien la hace queda como un gasto de esa
         * categoria (FIN-66). Que la categoria sea de gasto lo decide el caso de uso.
         */
        private static boolean transferenciaCompartida(CreateTransactionRequest movimiento,
                ConstraintValidatorContext context) {
            boolean valido = true;
            if (movimiento.destinationAccountId() != null) {
                valido = error(context, "destinationAccountId", "Una transferencia compartida no lleva cuenta destino");
            }
            if (movimiento.destinationAmount() != null) {
                valido = error(context, "destinationAmount", "Una transferencia compartida no lleva monto de destino");
            }
            if (vacio(movimiento.categoryId())) {
                valido = error(context, "categoryId", "Una transferencia compartida lleva una categoria de gasto");
            }
            return valido;
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
    }
}
