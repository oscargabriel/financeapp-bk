package com.oscargabriel.financeapp.domain.model;

/** Una moneda activa de finance.currencies, con lo que un cliente necesita para elegirla. */
public record Currency(String code, String name, String symbol) {
}
