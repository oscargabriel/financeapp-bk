package com.oscargabriel.financeapp.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CurrencyBalanceTest {

    @ParameterizedTest(name = "me deben {0}, debo {1}: neto {2}")
    @CsvSource({"30000, 10000, 20000", "0, 10000, -10000", "5000.5, 5000.5, 0", "20, 0, 20"})
    void elNetoEsLoQueMeDebenMenosLoQueDebo(BigDecimal aFavor, BigDecimal enContra, BigDecimal neto) {
        CurrencyBalance saldo = new CurrencyBalance("COP", aFavor, enContra);

        assertThat(saldo.net()).isEqualByComparingTo(neto);
    }
}
