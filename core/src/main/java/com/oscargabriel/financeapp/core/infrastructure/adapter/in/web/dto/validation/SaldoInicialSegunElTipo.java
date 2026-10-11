package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import com.oscargabriel.financeapp.core.domain.model.AccountType;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.CreateAccountRequest;

/**
 * Solo una tarjeta de credito arranca en negativo: ahi es deuda. En cualquier otro tipo seria un saldo imposible
 * (FA-133). El error sale sobre initialBalance, no sobre la clase. El parche lo decide UpdateAccountUseCase con el tipo
 * guardado, porque no trae tipo.
 */
@Retention(RUNTIME)
@Target(TYPE)
@Constraint(validatedBy = SaldoInicialSegunElTipo.Validador.class)
public @interface SaldoInicialSegunElTipo {

    String message() default "Solo una cuenta CREDIT puede tener saldo inicial negativo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<SaldoInicialSegunElTipo, CreateAccountRequest> {

        @Override
        public boolean isValid(CreateAccountRequest cuenta, ConstraintValidatorContext context) {
            AccountType tipo = CamposDeCredito.Validador.tipo(cuenta.type());
            // Con el tipo invalido no se sabe si el saldo negativo sobra: ya hay un error en type.
            if (tipo == null || tipo == AccountType.CREDIT
                    || cuenta.initialBalance() == null || cuenta.initialBalance().signum() >= 0) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("initialBalance")
                    .addConstraintViolation();
            return false;
        }
    }
}
