package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import com.oscargabriel.financeapp.domain.model.Frequency;
import com.oscargabriel.financeapp.domain.model.RecurrenceRule;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CreateRecurrenceRequest;

/**
 * Las reglas del alta de una serie que cruzan campos, cada error sobre el suyo: una serie es de gasto o
 * de ingreso; el dia y el tope del intervalo dependen de la frecuencia (RecurrenceRule.errores, la misma
 * regla que aplica la edicion); y el fin va por fecha o por numero, nunca antes del inicio. Un campo con
 * formato invalido ya tiene su error y no se vuelve a juzgar aqui.
 */
@Retention(RUNTIME)
@Target(TYPE)
@Constraint(validatedBy = ReglasDeSerie.Validador.class)
public @interface ReglasDeSerie {

    String message() default "La regla de la serie no es valida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<ReglasDeSerie, CreateRecurrenceRequest> {

        @Override
        public boolean isValid(CreateRecurrenceRequest alta, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            boolean valido = tipo(alta, context);
            valido &= regla(alta, context);
            valido &= fin(alta, context);
            return valido;
        }

        private static boolean tipo(CreateRecurrenceRequest alta, ConstraintValidatorContext context) {
            return constante(TransactionType.class, alta.type()) != TransactionType.TRANSFER
                    || error(context, "type", "Una serie solo puede ser de gastos o de ingresos");
        }

        private static boolean regla(CreateRecurrenceRequest alta, ConstraintValidatorContext context) {
            Frequency frecuencia = constante(Frequency.class, alta.frequency());
            if (frecuencia == null) {
                return true;
            }
            DayOfWeek diaSemana = constante(DayOfWeek.class, alta.dayOfWeek());
            boolean diaSemanaIlegible = diaSemana == null && !vacio(alta.dayOfWeek());
            boolean valido = true;
            for (var problema : RecurrenceRule.errores(frecuencia, alta.intervalOrDefault(), diaSemana,
                    alta.dayOfMonth())) {
                if (!(diaSemanaIlegible && "dayOfWeek".equals(problema.getField()))) {
                    valido = error(context, problema.getField(), problema.getDescription());
                }
            }
            return valido;
        }

        private static boolean fin(CreateRecurrenceRequest alta, ConstraintValidatorContext context) {
            if (vacio(alta.endDate())) {
                return true;
            }
            if (alta.occurrences() != null) {
                return error(context, "occurrences",
                        "Una serie termina por fecha o por numero de repeticiones, no por los dos");
            }
            LocalDate inicio = vacio(alta.startDate()) ? null : Fecha.Validador.leer(alta.startDate());
            LocalDate fin = Fecha.Validador.leer(alta.endDate());
            return inicio == null || fin == null || !fin.isBefore(inicio)
                    || error(context, "endDate", "La fecha de fin no puede ser anterior a la de inicio");
        }

        private static boolean error(ConstraintValidatorContext context, String campo, String texto) {
            context.buildConstraintViolationWithTemplate(texto).addPropertyNode(campo).addConstraintViolation();
            return false;
        }

        private static boolean vacio(String valor) {
            return valor == null || valor.isBlank();
        }

        /** La constante del enum con ese nombre, sin distinguir mayusculas; null si no hay. */
        private static <E extends Enum<E>> E constante(Class<E> tipo, String valor) {
            if (vacio(valor)) {
                return null;
            }
            try {
                return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
