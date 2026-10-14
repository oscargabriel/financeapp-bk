package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;

import com.oscargabriel.financeapp.core.domain.model.AffiliateBalance;
import com.oscargabriel.financeapp.core.domain.model.CurrencyBalance;

/** Lo que el usuario y otra persona se deben, por moneda (FIN-67). net positivo: la otra persona le debe. */
public record AffiliateBalanceResponse(
        String affiliationId,
        String status,
        SharingResponse.Person person,
        List<Balance> balances) {

    public record Balance(String currencyCode, BigDecimal owedToMe, BigDecimal iOwe, BigDecimal net) {

        static Balance from(CurrencyBalance saldo) {
            return new Balance(saldo.currencyCode(), saldo.owedToMe(), saldo.iOwe(), saldo.net());
        }
    }

    public static AffiliateBalanceResponse from(AffiliateBalance saldo) {
        return new AffiliateBalanceResponse(
                saldo.affiliationId().toString(),
                saldo.status().name(),
                SharingResponse.Person.from(saldo.person()),
                saldo.balances().stream().map(Balance::from).toList());
    }
}
