package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TasasTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "10", "2.15", "1.50000", "9.9999", "10.0000"})
    void admiteDeCeroADiezConHastaCuatroDecimales(String tasa) {
        assertThat(Tasas.cabe(new BigDecimal(tasa))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.0001", "-1", "10.0001", "28.5", "1.23456"})
    void rechazaLoNegativoLoMayorQueDiezYElQuintoDecimal(String tasa) {
        assertThat(Tasas.cabe(new BigDecimal(tasa))).isFalse();
    }
}
