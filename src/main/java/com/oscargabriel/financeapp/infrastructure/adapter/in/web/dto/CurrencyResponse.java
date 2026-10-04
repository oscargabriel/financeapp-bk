package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.domain.model.Currency;

public record CurrencyResponse(String code, String name, String symbol) {

    public static CurrencyResponse from(Currency currency) {
        return new CurrencyResponse(currency.code(), currency.name(), currency.symbol());
    }
}
