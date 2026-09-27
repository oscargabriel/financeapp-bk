package com.oscargabriel.financeapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class MonthRangeTest {

    private static final YearMonth MES_ACTUAL = YearMonth.of(2026, 9);

    @Test
    void usaLosDoceMesesQueTerminanEnElMesActualCuandoNoLlegaNingunExtremo() {
        MonthRange resultado = MonthRange.resolve(null, null, MES_ACTUAL);

        assertThat(resultado.from()).isEqualTo(YearMonth.of(2025, 10));
        assertThat(resultado.to()).isEqualTo(MES_ACTUAL);
    }

    @Test
    void terminaEnElMesActualCuandoSoloLlegaElInicio() {
        MonthRange resultado = MonthRange.resolve(YearMonth.of(2026, 7), null, MES_ACTUAL);

        assertThat(resultado.from()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(resultado.to()).isEqualTo(MES_ACTUAL);
    }

    @Test
    void retrocedeOnceMesesCuandoSoloLlegaElFin() {
        MonthRange resultado = MonthRange.resolve(null, YearMonth.of(2026, 3), MES_ACTUAL);

        assertThat(resultado.from()).isEqualTo(YearMonth.of(2025, 4));
        assertThat(resultado.to()).isEqualTo(YearMonth.of(2026, 3));
    }

    @Test
    void conservaAmbosExtremosCuandoLleganLosDos() {
        MonthRange resultado = MonthRange.resolve(YearMonth.of(2024, 1), YearMonth.of(2026, 9), MES_ACTUAL);

        assertThat(resultado.from()).isEqualTo(YearMonth.of(2024, 1));
        assertThat(resultado.to()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void aceptaUnRangoDeUnSoloMes() {
        MonthRange resultado = MonthRange.resolve(MES_ACTUAL, MES_ACTUAL, MES_ACTUAL);

        assertThat(resultado.from()).isEqualTo(MES_ACTUAL);
        assertThat(resultado.to()).isEqualTo(MES_ACTUAL);
    }

    @Test
    void rechazaElRangoCuandoElInicioEsPosteriorAlFin() {
        assertThatThrownBy(() -> MonthRange.resolve(YearMonth.of(2026, 10), YearMonth.of(2026, 9), MES_ACTUAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaUnInicioFuturoSinFinPorqueElFinPasaASerElMesActual() {
        assertThatThrownBy(() -> MonthRange.resolve(YearMonth.of(2027, 1), null, MES_ACTUAL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
