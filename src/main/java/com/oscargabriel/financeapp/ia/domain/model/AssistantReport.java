package com.oscargabriel.financeapp.ia.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * El reporte liviano del asistente (FA-148). Los totales van en la moneda del usuario y salen de
 * amountBase; la lista lleva cada monto en la moneda de su movimiento.
 */
public record AssistantReport(
        LocalDate from,
        LocalDate to,
        String currencyCode,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net,
        List<Item> transactions) {

    public record Item(
            UUID id,
            TransactionType type,
            BigDecimal amount,
            String currencyCode,
            BigDecimal amountBase,
            String description,
            Instant occurredAt,
            String account,
            String category) {
    }

    /** Las transferencias salen en la lista pero no mueven ingresos ni gastos. */
    public static AssistantReport of(LocalDate from, LocalDate to, String currencyCode, List<Item> transactions) {
        BigDecimal income = suma(transactions, TransactionType.INCOME);
        BigDecimal expense = suma(transactions, TransactionType.EXPENSE);
        return new AssistantReport(from, to, currencyCode, income, expense, income.subtract(expense),
                List.copyOf(transactions));
    }

    private static BigDecimal suma(List<Item> transactions, TransactionType tipo) {
        return transactions.stream().filter(t -> t.type() == tipo).map(Item::amountBase)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
