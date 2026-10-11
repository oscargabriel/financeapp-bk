package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.oscargabriel.financeapp.domain.model.ExchangeRate;

public record ExchangeRateResponse(String from, String to, LocalDate date, BigDecimal rate, LocalDate rateDate) {

    public static ExchangeRateResponse from(ExchangeRate tasa) {
        return new ExchangeRateResponse(tasa.from(), tasa.to(), tasa.date(), tasa.rate(), tasa.rateDate());
    }
}
