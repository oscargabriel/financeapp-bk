package com.oscargabriel.financeapp.domain.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;

/**
 * Las fechas de una serie (FA-107): la primera es el primer dia pedido desde startDate, y las
 * siguientes caen cada interval semanas o meses. dayOfWeek va solo en WEEKLY y dayOfMonth solo en
 * MONTHLY: lo comprueba errores(...), que usan el alta y la edicion antes de construir la regla.
 */
public record RecurrenceRule(Frequency frequency, int interval, DayOfWeek dayOfWeek, Integer dayOfMonth,
        LocalDate startDate) {

    public static RecurrenceRule semanal(int interval, DayOfWeek dia, LocalDate inicio) {
        return new RecurrenceRule(Frequency.WEEKLY, interval, dia, null, inicio);
    }

    public static RecurrenceRule mensual(int interval, int dia, LocalDate inicio) {
        return new RecurrenceRule(Frequency.MONTHLY, interval, null, dia, inicio);
    }

    /** La misma regla desde otro dia. */
    public RecurrenceRule desde(LocalDate inicio) {
        return new RecurrenceRule(frequency, interval, dayOfWeek, dayOfMonth, inicio);
    }

    /**
     * La n-esima ocurrencia, desde 1. En MONTHLY el dia se ancla al pedido y no al de la ocurrencia
     * anterior: 31 da 31-ene, 28-feb, 31-mar.
     */
    public LocalDate occurrence(int n) {
        long saltos = (long) (n - 1) * interval;
        if (frequency == Frequency.WEEKLY) {
            return startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek)).plusWeeks(saltos);
        }
        YearMonth primerMes = YearMonth.from(startDate);
        if (enElMes(primerMes).isBefore(startDate)) {
            primerMes = primerMes.plusMonths(1);
        }
        return enElMes(primerMes.plusMonths(saltos));
    }

    /**
     * Medianoche del dia en la zona del usuario: la ocurrencia de hoy ya ocurrio. Si un cambio de
     * horario se salta la medianoche, es la primera hora que existe ese dia.
     */
    public static Instant medianoche(LocalDate dia, ZoneId zona) {
        return dia.atStartOfDay(zona).toInstant();
    }

    /**
     * Los errores de forma de una regla, cada uno sobre su campo. Anual es MONTHLY cada 12 meses, y por
     * eso el tope del intervalo depende de la frecuencia. En la edicion los dias heredados de la regla
     * guardada llegan ya en null si no aplican a la frecuencia resultante: solo el enviado es error.
     */
    public static List<ErrorDetail> errores(Frequency frecuencia, int interval, DayOfWeek diaSemana, Integer diaMes) {
        List<ErrorDetail> errores = new ArrayList<>();
        boolean semanal = frecuencia == Frequency.WEEKLY;
        if (interval < 1) {
            errores.add(error("El intervalo es al menos 1", "interval"));
        } else if (semanal && interval > 52) {
            errores.add(error("Una serie semanal se repite como mucho cada 52 semanas", "interval"));
        } else if (!semanal && interval > 12) {
            errores.add(error("Una serie mensual se repite como mucho cada 12 meses: anual es 12", "interval"));
        }
        if (semanal) {
            if (diaSemana == null) {
                errores.add(error("Una serie semanal necesita dayOfWeek", "dayOfWeek"));
            }
            if (diaMes != null) {
                errores.add(error("Solo una serie mensual lleva dayOfMonth", "dayOfMonth"));
            }
        } else {
            if (diaMes == null) {
                errores.add(error("Una serie mensual necesita dayOfMonth", "dayOfMonth"));
            } else if (diaMes < 1 || diaMes > 31) {
                errores.add(error("El dia del mes va de 1 a 31", "dayOfMonth"));
            }
            if (diaSemana != null) {
                errores.add(error("Solo una serie semanal lleva dayOfWeek", "dayOfWeek"));
            }
        }
        return errores;
    }

    private static ErrorDetail error(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(), descripcion, campo);
    }

    private LocalDate enElMes(YearMonth mes) {
        return mes.atDay(Math.min(dayOfMonth, mes.lengthOfMonth()));
    }
}
