package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.stream.Stream;

/**
 * Unidades de to por una de from en date, calculadas contra USD (design.md de FA-120). rateDate es la
 * fecha de la pata mas vieja: dice que tan antiguo es el dato usado.
 */
public record ExchangeRate(String from, String to, LocalDate date, BigDecimal rate, LocalDate rateDate) {

    /** La escala de exchange_rate en transactions, donde FA-51 guarda la tasa. */
    private static final int ESCALA = 10;

    /** desde es la pata USD→from y hacia la USD→to: from→to = (USD→to) / (USD→from). */
    public static ExchangeRate cruzada(LocalDate date, UsdRate desde, UsdRate hacia) {
        LocalDate masVieja = Stream.of(desde.rateDate(), hacia.rateDate())
                .filter(fecha -> fecha != null)
                .min(LocalDate::compareTo)
                .orElse(date);
        return new ExchangeRate(desde.currency(), hacia.currency(), date,
                hacia.rate().divide(desde.rate(), ESCALA, RoundingMode.HALF_EVEN), masVieja);
    }

    public static ExchangeRate mismoPar(String moneda, LocalDate date) {
        return new ExchangeRate(moneda, moneda, date, BigDecimal.ONE, date);
    }

    /**
     * monto en from, llevado a to y redondeado a sus decimales (FA-51). Nunca da cero: ck_transactions_amount
     * exige un monto positivo, asi que lo que redondea a cero vale la unidad minima de esa escala.
     */
    public BigDecimal convertir(BigDecimal monto, int decimales) {
        BigDecimal convertido = monto.multiply(rate).setScale(decimales, RoundingMode.HALF_EVEN);
        return convertido.signum() > 0 ? convertido : BigDecimal.ONE.movePointLeft(decimales);
    }
}
