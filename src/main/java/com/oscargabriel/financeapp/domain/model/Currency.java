package com.oscargabriel.financeapp.domain.model;

/**
 * Una moneda activa de finance.currencies. decimalPlaces es la escala a la que se redondea un monto convertido
 * a esta moneda (FA-51); el catalogo del API no la expone.
 */
public record Currency(String code, String name, String symbol, int decimalPlaces) {
}
