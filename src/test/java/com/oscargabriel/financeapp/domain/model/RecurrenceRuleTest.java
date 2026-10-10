package com.oscargabriel.financeapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;

class RecurrenceRuleTest {

    /** Un miercoles. */
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 14);

    @Test
    void semanalEmpiezaElPrimerDiaPedidoDesdeElInicio() {
        RecurrenceRule regla = RecurrenceRule.semanal(1, DayOfWeek.MONDAY, MIERCOLES);

        assertThat(regla.occurrence(1)).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void semanalIncluyeElInicioSiCaeEnElDiaPedido() {
        RecurrenceRule regla = RecurrenceRule.semanal(1, DayOfWeek.WEDNESDAY, MIERCOLES);

        assertThat(regla.occurrence(1)).isEqualTo(MIERCOLES);
    }

    @Test
    void semanalCadaDosSemanas() {
        RecurrenceRule regla = RecurrenceRule.semanal(2, DayOfWeek.MONDAY, MIERCOLES);

        assertThat(IntStream.rangeClosed(1, 3).mapToObj(regla::occurrence))
                .containsExactly(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 16));
    }

    @Test
    void mensualEnElMesDeInicioSiElDiaNoHaPasado() {
        RecurrenceRule regla = RecurrenceRule.mensual(1, 15, LocalDate.of(2026, 10, 9));

        assertThat(regla.occurrence(1)).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void mensualPasaAlMesSiguienteSiElDiaYaPaso() {
        RecurrenceRule regla = RecurrenceRule.mensual(1, 5, LocalDate.of(2026, 10, 9));

        assertThat(regla.occurrence(1)).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void mensualElDia31CaeElUltimoDiaSinMoverLasSiguientes() {
        RecurrenceRule regla = RecurrenceRule.mensual(1, 31, LocalDate.of(2027, 1, 31));

        assertThat(IntStream.rangeClosed(1, 4).mapToObj(regla::occurrence)).containsExactly(
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31),
                LocalDate.of(2027, 4, 30));
    }

    @Test
    void mensualElDia29DeFebreroEnBisiesto() {
        RecurrenceRule regla = RecurrenceRule.mensual(1, 31, LocalDate.of(2028, 2, 1));

        assertThat(regla.occurrence(1)).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void mensualConElDiaRecortadoTodaviaPorVenirEnElMesDeInicio() {
        RecurrenceRule regla = RecurrenceRule.mensual(1, 31, LocalDate.of(2027, 2, 20));

        assertThat(regla.occurrence(1)).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void anualEsMensualCadaDoceMeses() {
        RecurrenceRule regla = RecurrenceRule.mensual(12, 10, LocalDate.of(2026, 10, 1));

        assertThat(regla.occurrence(2)).isEqualTo(LocalDate.of(2027, 10, 10));
    }

    @Test
    void cadaOcurrenciaEsLaMedianocheEnLaZonaDelUsuario() {
        Instant instante = RecurrenceRule.medianoche(LocalDate.of(2026, 10, 15), ZoneId.of("America/Bogota"));

        assertThat(instante).isEqualTo(Instant.parse("2026-10-15T05:00:00Z"));
    }

    @Test
    void laMedianocheQueNoExistePorElCambioDeHorarioEsLaPrimeraHoraDelDia() {
        // En Santiago el reloj salta de 00:00 a 01:00 el primer domingo de septiembre.
        Instant instante = RecurrenceRule.medianoche(LocalDate.of(2026, 9, 6), ZoneId.of("America/Santiago"));

        assertThat(instante).isEqualTo(Instant.parse("2026-09-06T04:00:00Z"));
    }

    @Test
    void unaReglaBienFormadaNoTieneErrores() {
        assertThat(RecurrenceRule.errores(Frequency.WEEKLY, 52, DayOfWeek.MONDAY, null)).isEmpty();
        assertThat(RecurrenceRule.errores(Frequency.MONTHLY, 12, null, 31)).isEmpty();
    }

    @Test
    void elIntervaloEsAlMenosUno() {
        assertThat(campos(RecurrenceRule.errores(Frequency.MONTHLY, 0, null, 5))).containsExactly("interval");
    }

    @Test
    void semanalHastaCada52Semanas() {
        assertThat(campos(RecurrenceRule.errores(Frequency.WEEKLY, 53, DayOfWeek.MONDAY, null)))
                .containsExactly("interval");
    }

    @Test
    void mensualHastaCada12Meses() {
        assertThat(campos(RecurrenceRule.errores(Frequency.MONTHLY, 13, null, 5))).containsExactly("interval");
    }

    @Test
    void semanalExigeElDiaDeLaSemanaYNoLlevaDiaDelMes() {
        assertThat(campos(RecurrenceRule.errores(Frequency.WEEKLY, 1, null, 5)))
                .containsExactlyInAnyOrder("dayOfWeek", "dayOfMonth");
    }

    @Test
    void mensualExigeElDiaDelMesYNoLlevaDiaDeLaSemana() {
        assertThat(campos(RecurrenceRule.errores(Frequency.MONTHLY, 1, DayOfWeek.MONDAY, null)))
                .containsExactlyInAnyOrder("dayOfWeek", "dayOfMonth");
    }

    @Test
    void elDiaDelMesVaDe1A31() {
        assertThat(campos(RecurrenceRule.errores(Frequency.MONTHLY, 1, null, 32))).containsExactly("dayOfMonth");
        assertThat(campos(RecurrenceRule.errores(Frequency.MONTHLY, 1, null, 0))).containsExactly("dayOfMonth");
    }

    @Test
    void losErroresSonDeValidacion() {
        assertThat(RecurrenceRule.errores(Frequency.MONTHLY, 0, null, 32))
                .extracting(ErrorDetail::getCode).containsOnly("VALIDATION_ERROR");
    }

    private static List<String> campos(List<ErrorDetail> errores) {
        return errores.stream().map(ErrorDetail::getField).toList();
    }
}
