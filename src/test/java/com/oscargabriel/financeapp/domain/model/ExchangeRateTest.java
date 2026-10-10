package com.oscargabriel.financeapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ExchangeRateTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 10);

    private static UsdRate usdA(String moneda, String tasa, LocalDate fecha) {
        return new UsdRate(moneda, new BigDecimal(tasa), fecha);
    }

    @Test
    void cruzaLasDosPatasContraUsd() {
        ExchangeRate tasa = ExchangeRate.cruzada(HOY, usdA("EUR", "0.8", HOY), usdA("COP", "4100", HOY));

        assertThat(tasa.from()).isEqualTo("EUR");
        assertThat(tasa.to()).isEqualTo("COP");
        assertThat(tasa.date()).isEqualTo(HOY);
        assertThat(tasa.rate()).isEqualByComparingTo("5125");
        assertThat(tasa.rateDate()).isEqualTo(HOY);
    }

    @Test
    void redondeaElParInversoADiezDecimales() {
        ExchangeRate tasa = ExchangeRate.cruzada(HOY, usdA("COP", "4100", HOY), UsdRate.usd());

        assertThat(tasa.rate()).isEqualTo(new BigDecimal("0.0002439024"));
        assertThat(tasa.to()).isEqualTo("USD");
    }

    @Test
    void laPataDeUsdValeUnoYNoDaFecha() {
        ExchangeRate tasa = ExchangeRate.cruzada(HOY, UsdRate.usd(), usdA("COP", "3900", HOY.minusDays(30)));

        assertThat(tasa.from()).isEqualTo("USD");
        assertThat(tasa.rate()).isEqualByComparingTo("3900");
        assertThat(tasa.rateDate()).isEqualTo(HOY.minusDays(30));
    }

    @Test
    void laFechaDeLaTasaEsLaDeLaPataMasVieja() {
        ExchangeRate tasa = ExchangeRate.cruzada(HOY, usdA("EUR", "0.8", HOY.minusDays(3)),
                usdA("COP", "4100", HOY.minusDays(1)));

        assertThat(tasa.rateDate()).isEqualTo(HOY.minusDays(3));
    }

    @Test
    void elMismoParValeUnoEnLaFechaPedida() {
        ExchangeRate tasa = ExchangeRate.mismoPar("COP", LocalDate.of(2026, 1, 15));

        assertThat(tasa.from()).isEqualTo("COP");
        assertThat(tasa.to()).isEqualTo("COP");
        assertThat(tasa.rate()).isEqualByComparingTo("1");
        assertThat(tasa.rateDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }
}
