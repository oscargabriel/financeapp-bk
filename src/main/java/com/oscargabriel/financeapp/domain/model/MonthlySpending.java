package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Una fila de finance.v_monthly_spending. budgetAmount, remaining y percentUsed son null cuando el
 * mes no tiene meta definida: null significa "no configuro meta", que no es lo mismo que cero.
 */
public record MonthlySpending(
        YearMonth periodMonth,
        String currencyCode,
        BigDecimal totalSpent,
        BigDecimal budgetAmount,
        BigDecimal remaining,
        BigDecimal percentUsed,
        long transactionCount) {
}
