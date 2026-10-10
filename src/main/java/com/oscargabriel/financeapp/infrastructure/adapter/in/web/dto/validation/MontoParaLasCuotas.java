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

import com.oscargabriel.financeapp.domain.model.InstallmentPlan;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CreateInstallmentPurchaseRequest;

/**
 * El capital de cada cuota es el total entre las cuotas en pesos enteros hacia abajo (FA-108): con un
 * monto menor que el numero de cuotas, alguna quedaria con capital cero. El error va sobre amount. Un
 * monto o un numero de cuotas con su propio error no se vuelve a juzgar aqui.
 */
@Retention(RUNTIME)
@Target(TYPE)
@Constraint(validatedBy = MontoParaLasCuotas.Validador.class)
public @interface MontoParaLasCuotas {

    String message() default "El monto tiene que alcanzar para al menos 1 peso de capital por cuota";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<MontoParaLasCuotas, CreateInstallmentPurchaseRequest> {

        @Override
        public boolean isValid(CreateInstallmentPurchaseRequest compra, ConstraintValidatorContext context) {
            Integer cuotas = compra.installmentCount();
            BigDecimal monto = compra.amount();
            if (cuotas == null || cuotas < 1 || cuotas > InstallmentPlan.MAXIMO
                    || monto == null || monto.signum() <= 0 || !Montos.cabe(monto)
                    || monto.compareTo(BigDecimal.valueOf(cuotas)) >= 0) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("amount")
                    .addConstraintViolation();
            return false;
        }
    }
}