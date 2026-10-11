package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * La consulta de saldo: ingresos y gastos de un rango y de siempre, en la moneda base del usuario, y
 * sus cuentas activas. Las cuentas no se suman: cada una va en su moneda (ver el design.md de FA-75).
 */
public record Balance(
        String currencyCode,
        LocalDate from,
        LocalDate to,
        Totals period,
        Totals allTime,
        List<Account> accounts) {

    /** Sobre amountBase. Las transferencias no entran: no son ni ingreso ni gasto. */
    public record Totals(BigDecimal income, BigDecimal expense) {

        public BigDecimal net() {
            return income.subtract(expense);
        }
    }

    /** Lo que sale de la base: la moneda base y las sumas del rango y del historico. */
    public record Sums(String currencyCode, Totals period, Totals allTime) {
    }

    public static Balance of(LocalDate from, LocalDate to, Sums sums, List<Account> accounts) {
        return new Balance(sums.currencyCode(), from, to, sums.period(), sums.allTime(), List.copyOf(accounts));
    }
}
