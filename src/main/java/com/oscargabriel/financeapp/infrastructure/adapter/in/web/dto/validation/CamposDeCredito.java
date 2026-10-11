package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.math.BigDecimal;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CreateAccountRequest;

/**
 * Cupo, dia de corte, dia de pago y tasa de interes dependen del tipo: obligatorios en rango para una CREDIT y
 * prohibidos en las demas. Cada error sale sobre su propio campo, no sobre la clase. Tienen que salir
 * como 400 y no llegar a ck_accounts_credit_fields.
 */
@Retention(RUNTIME)
@Target(TYPE)
@Constraint(validatedBy = CamposDeCredito.Validador.class)
public @interface CamposDeCredito {

    String message() default "Los campos de credito no corresponden al tipo de cuenta";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<CamposDeCredito, CreateAccountRequest> {

        @Override
        public boolean isValid(CreateAccountRequest cuenta, ConstraintValidatorContext context) {
            AccountType tipo = tipo(cuenta.type());
            // Con el tipo invalido no se sabe si los campos sobran: ya hay un error en type.
            if (tipo == null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            return tipo == AccountType.CREDIT
                    ? enRango(cuenta, context)
                    : ausentes(cuenta, context);
        }

        private static boolean enRango(CreateAccountRequest cuenta, ConstraintValidatorContext context) {
            boolean valido = true;
            BigDecimal limite = cuenta.creditLimit();
            if (limite != null && (limite.signum() <= 0 || !Montos.cabe(limite))) {
                valido = error(context, "creditLimit", "El cupo debe ser mayor que cero y" + Montos.FUERA_DE_RANGO);
            }
            if (!esDiaDelMes(cuenta.statementDay())) {
                valido = error(context, "statementDay", "El dia de corte debe estar entre 1 y 31");
            }
            if (!esDiaDelMes(cuenta.paymentDueDay())) {
                valido = error(context, "paymentDueDay", "El dia de pago debe estar entre 1 y 31");
            }
            BigDecimal tasa = cuenta.monthlyInterestRate();
            if (tasa != null && !Tasas.cabe(tasa)) {
                valido = error(context, "monthlyInterestRate", Tasas.FUERA_DE_RANGO);
            }
            return valido;
        }

        private static boolean ausentes(CreateAccountRequest cuenta, ConstraintValidatorContext context) {
            boolean valido = true;
            if (cuenta.creditLimit() != null) {
                valido = error(context, "creditLimit", "Solo una cuenta CREDIT tiene cupo");
            }
            if (cuenta.statementDay() != null) {
                valido = error(context, "statementDay", "Solo una cuenta CREDIT tiene dia de corte");
            }
            if (cuenta.paymentDueDay() != null) {
                valido = error(context, "paymentDueDay", "Solo una cuenta CREDIT tiene dia de pago");
            }
            if (cuenta.monthlyInterestRate() != null) {
                valido = error(context, "monthlyInterestRate", "Solo una cuenta CREDIT tiene tasa de interes");
            }
            return valido;
        }

        private static boolean error(ConstraintValidatorContext context, String campo, String texto) {
            context.buildConstraintViolationWithTemplate(texto).addPropertyNode(campo).addConstraintViolation();
            return false;
        }

        private static boolean esDiaDelMes(Integer dia) {
            return dia == null || (dia >= 1 && dia <= 31);
        }

        private static AccountType tipo(String valor) {
            if (valor == null || valor.isBlank()) {
                return null;
            }
            try {
                return AccountType.valueOf(valor.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
