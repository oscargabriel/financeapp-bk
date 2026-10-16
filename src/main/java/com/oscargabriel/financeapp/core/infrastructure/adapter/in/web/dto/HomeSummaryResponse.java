package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;

import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.HomeSummary;
import com.oscargabriel.financeapp.core.domain.model.ReportedTransaction;

/**
 * El resumen de Inicio (FIN-107). Los montos de months, spent y debitBalance van en currencyCode; amount de cada
 * gasto y los de cada cuenta, en la suya. occurredAt sale en UTC, como en el resto del API.
 */
public record HomeSummaryResponse(
        String currencyCode,
        String month,
        List<MonthItem> months,
        BigDecimal spent,
        BigDecimal spentChangePercent,
        List<ExpenseItem> latestExpenses,
        List<TopExpenseItem> topExpenses,
        BigDecimal debitBalance,
        List<AccountItem> accounts) {

    public record MonthItem(String periodMonth, BigDecimal totalIncome, BigDecimal totalSpent) {

        static MonthItem from(HomeSummary.Month mes) {
            return new MonthItem(mes.periodMonth().toString(), mes.totalIncome(), mes.totalSpent());
        }
    }

    public record ExpenseItem(
            String id,
            String description,
            String categoryName,
            String occurredAt,
            BigDecimal amount,
            String currencyCode,
            BigDecimal amountBase) {

        static ExpenseItem from(ReportedTransaction t) {
            return new ExpenseItem(t.id().toString(), t.description(), t.categoryName(), t.occurredAt().toString(),
                    t.amount(), t.currencyCode(), t.amountBase());
        }
    }

    public record TopExpenseItem(
            String id,
            String description,
            String categoryName,
            String occurredAt,
            BigDecimal amount,
            String currencyCode,
            BigDecimal amountBase,
            BigDecimal shareOfTop) {

        static TopExpenseItem from(HomeSummary.TopExpense gasto) {
            ReportedTransaction t = gasto.expense();
            return new TopExpenseItem(t.id().toString(), t.description(), t.categoryName(),
                    t.occurredAt().toString(), t.amount(), t.currencyCode(), t.amountBase(), gasto.shareOfTop());
        }
    }

    /** Los campos de las cuentas de la consulta de saldo, mas el cupo usado. */
    public record AccountItem(
            String id,
            String name,
            String type,
            String currencyCode,
            BigDecimal currentBalance,
            BigDecimal creditLimit,
            BigDecimal availableCredit,
            BigDecimal creditUsedPercent) {

        static AccountItem from(HomeSummary.AccountUsage uso) {
            Account cuenta = uso.account();
            return new AccountItem(cuenta.id().toString(), cuenta.name(), cuenta.type().name(), cuenta.currencyCode(),
                    cuenta.currentBalance(), cuenta.creditLimit(), cuenta.availableCredit(), uso.creditUsedPercent());
        }
    }

    public static HomeSummaryResponse from(HomeSummary resumen) {
        return new HomeSummaryResponse(
                resumen.currencyCode(),
                resumen.month().toString(),
                resumen.months().stream().map(MonthItem::from).toList(),
                resumen.spent(),
                resumen.spentChangePercent(),
                resumen.latestExpenses().stream().map(ExpenseItem::from).toList(),
                resumen.topExpenses().stream().map(TopExpenseItem::from).toList(),
                resumen.debitBalance(),
                resumen.accounts().stream().map(AccountItem::from).toList());
    }
}
