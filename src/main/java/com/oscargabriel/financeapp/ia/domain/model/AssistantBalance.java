package com.oscargabriel.financeapp.ia.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** El saldo liviano del asistente (FA-148): los totales en la moneda del usuario, cada cuenta en la suya. */
public record AssistantBalance(
        LocalDate from,
        LocalDate to,
        String currencyCode,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net,
        BigDecimal allTimeNet,
        List<AccountBalance> accounts) {

    public record AccountBalance(String name, String currencyCode, BigDecimal currentBalance) {
    }

    public record Sums(String currencyCode, BigDecimal periodIncome, BigDecimal periodExpense,
            BigDecimal allTimeIncome, BigDecimal allTimeExpense) {
    }

    public static AssistantBalance of(LocalDate from, LocalDate to, Sums sums, List<AccountBalance> accounts) {
        return new AssistantBalance(from, to, sums.currencyCode(), sums.periodIncome(), sums.periodExpense(),
                sums.periodIncome().subtract(sums.periodExpense()),
                sums.allTimeIncome().subtract(sums.allTimeExpense()), List.copyOf(accounts));
    }
}
