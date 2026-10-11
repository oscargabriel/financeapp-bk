package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;

import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.Balance;

/** La consulta de saldo. Los montos de period y allTime van en currencyCode; los de cada cuenta, en la suya. */
public record BalanceResponse(
        String currencyCode,
        Period period,
        Totals allTime,
        List<AccountItem> accounts) {

    public record Period(String from, String to, BigDecimal income, BigDecimal expense, BigDecimal net) {
    }

    public record Totals(BigDecimal income, BigDecimal expense, BigDecimal net) {

        static Totals from(Balance.Totals totales) {
            return new Totals(totales.income(), totales.expense(), totales.net());
        }
    }

    public record AccountItem(
            String id,
            String name,
            String type,
            String currencyCode,
            BigDecimal currentBalance,
            BigDecimal creditLimit,
            BigDecimal availableCredit) {

        static AccountItem from(Account cuenta) {
            return new AccountItem(
                    cuenta.id().toString(),
                    cuenta.name(),
                    cuenta.type().name(),
                    cuenta.currencyCode(),
                    cuenta.currentBalance(),
                    cuenta.creditLimit(),
                    cuenta.availableCredit());
        }
    }

    public static BalanceResponse from(Balance saldo) {
        Balance.Totals periodo = saldo.period();
        return new BalanceResponse(
                saldo.currencyCode(),
                new Period(saldo.from().toString(), saldo.to().toString(), periodo.income(), periodo.expense(),
                        periodo.net()),
                Totals.from(saldo.allTime()),
                saldo.accounts().stream().map(AccountItem::from).toList());
    }
}
