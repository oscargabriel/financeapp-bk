package com.oscargabriel.financeapp.domain.model;

import static com.oscargabriel.financeapp.support.RecurrenceMother.HOY;
import static com.oscargabriel.financeapp.support.RecurrenceMother.SERIE_ID;
import static com.oscargabriel.financeapp.support.RecurrenceMother.unaSerie;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.oscargabriel.financeapp.support.TransactionMother;

class RecurrenceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Test
    void conNumeroDeRepeticionesCreaExactamenteEsas() {
        Recurrence serie = unaSerie().mensual(1, 15, HOY).veces(3).build();

        assertThat(serie.pendientes(HOY)).containsExactly(
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 15));
    }

    @Test
    void conFechaDeFinCreaHastaEsaFechaIncluida() {
        LocalDate lunes = LocalDate.of(2026, 10, 12);
        Recurrence serie = unaSerie().semanal(1, DayOfWeek.MONDAY, lunes).hasta(lunes.plusDays(21)).build();

        assertThat(serie.pendientes(HOY)).hasSize(4).last().isEqualTo(lunes.plusDays(21));
    }

    @Test
    void conFinNoDependeDeHoy() {
        Recurrence serie = unaSerie().mensual(1, 1, HOY.minusMonths(3)).veces(2).build();

        assertThat(serie.pendientes(HOY)).containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));
    }

    @Test
    void sinFinCreaHastaHoyMasLaSiguiente() {
        Recurrence serie = unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY.minusDays(21)).build();

        assertThat(serie.pendientes(HOY)).containsExactly(
                HOY.minusDays(21), HOY.minusDays(14), HOY.minusDays(7), HOY, HOY.plusDays(7));
    }

    @Test
    void sinFinQueEmpiezaEnElFuturoCreaSoloLaPrimera() {
        Recurrence serie = unaSerie().mensual(1, 19, HOY.plusDays(10)).build();

        assertThat(serie.pendientes(HOY)).containsExactly(HOY.plusDays(10));
    }

    @Test
    void laPuestaAlDiaParteDelContadorYNoRecreaLasYaCreadas() {
        Recurrence serie = unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY.minusDays(28)).creadas(2).build();

        assertThat(serie.pendientes(HOY)).containsExactly(
                HOY.minusDays(14), HOY.minusDays(7), HOY, HOY.plusDays(7));
    }

    @Test
    void unaSinFinConLaSiguienteYaCreadaNoTienePendientes() {
        Recurrence serie = unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY.minusDays(7)).creadas(3).build();

        assertThat(serie.pendientes(HOY)).isEmpty();
    }

    @Test
    void cortaUnaMasAllaDelTopeParaQueQuienLlamaDetecteElExceso() {
        Recurrence serie = unaSerie().semanal(1, DayOfWeek.MONDAY, HOY).hasta(HOY.plusYears(10)).build();

        assertThat(serie.pendientes(HOY)).hasSize(Recurrence.TOPE + 1);
    }

    @Test
    void sinFinQueEmpezoHaceMuchoTambienCortaEnElTope() {
        Recurrence serie = unaSerie().semanal(1, DayOfWeek.MONDAY, HOY.minusYears(10)).build();

        assertThat(serie.pendientes(HOY)).hasSize(Recurrence.TOPE + 1);
    }

    @Test
    void cadaOcurrenciaEsUnMovimientoConfirmadoDeLaSerieAMedianoche() {
        Recurrence serie = unaSerie().build();
        UUID id = UUID.randomUUID();

        Transaction ocurrencia = serie.ocurrencia(id, LocalDate.of(2026, 10, 15), BOGOTA);

        assertThat(ocurrencia).isEqualTo(new Transaction(id, TransactionMother.USER_ID, TransactionType.EXPENSE,
                TransactionMother.ORIGEN_ID, null, TransactionMother.MERCADO_ID, new BigDecimal("44900"), "COP",
                "Netflix", null, Instant.parse("2026-10-15T05:00:00Z"), TransactionStatus.CONFIRMED,
                TransactionOrigin.WEB, SERIE_ID, null));
    }

    @Test
    void conGeneradasSumaAlContador() {
        assertThat(unaSerie().creadas(2).build().conGeneradas(3).generatedCount()).isEqualTo(5);
    }

    @Test
    void sinFinEsLaQueNoTieneNiFechaNiNumero() {
        assertThat(unaSerie().build().openEnded()).isTrue();
        assertThat(unaSerie().veces(3).build().openEnded()).isFalse();
        assertThat(unaSerie().hasta(HOY).build().openEnded()).isFalse();
    }

    @Test
    void laSiguienteACrearEsLaDelContadorMasUno() {
        Recurrence serie = unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY.minusDays(7)).creadas(1).build();

        assertThat(serie.siguienteACrear()).isEqualTo(HOY);
    }

    @Test
    void cambiarLaReglaDescuentaLasOcurridasYEmpiezaManana() {
        Recurrence serie = unaSerie().mensual(1, 1, LocalDate.of(2026, 9, 1)).veces(6).creadas(6).build();

        Recurrence nueva = serie.conRegla(RecurrenceRule.semanal(1, DayOfWeek.FRIDAY, null), HOY);

        assertThat(nueva.priorCount()).isEqualTo(2);
        assertThat(nueva.generatedCount()).isZero();
        assertThat(nueva.rule().startDate()).isEqualTo(HOY.plusDays(1));
        assertThat(nueva.pendientes(HOY)).containsExactly(
                HOY.plusDays(7), HOY.plusDays(14), HOY.plusDays(21), HOY.plusDays(28));
    }

    @Test
    void cambiarLaReglaDeUnaSinFinDejaSoloLaSiguiente() {
        Recurrence serie = unaSerie().semanal(1, DayOfWeek.FRIDAY, HOY.minusDays(14)).creadas(4).build();

        Recurrence nueva = serie.conRegla(RecurrenceRule.mensual(1, 20, null), HOY);

        assertThat(nueva.priorCount()).isEqualTo(3);
        assertThat(nueva.pendientes(HOY)).containsExactly(LocalDate.of(2026, 10, 20));
    }

    @Test
    void cambiarLaReglaDeUnaConFechaDeFinLlegaHastaEsaFecha() {
        Recurrence serie = unaSerie().mensual(1, 1, LocalDate.of(2026, 9, 1)).hasta(LocalDate.of(2026, 10, 31))
                .creadas(2).build();

        Recurrence nueva = serie.conRegla(RecurrenceRule.semanal(2, DayOfWeek.MONDAY, null), HOY);

        assertThat(nueva.pendientes(HOY)).containsExactly(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 26));
    }

    @Test
    void cambiarLaReglaCuandoYaNoQuedanRepeticionesNoCreaNada() {
        Recurrence serie = unaSerie().mensual(1, 1, LocalDate.of(2026, 9, 1)).veces(2).creadas(2).build();

        assertThat(serie.conRegla(RecurrenceRule.mensual(1, 20, null), HOY).pendientes(HOY)).isEmpty();
    }

    @Test
    void conPlantillaCambiaSoloLosDatosDelMovimiento() {
        UUID cuenta = UUID.randomUUID();
        UUID categoria = UUID.randomUUID();
        Recurrence serie = unaSerie().creadas(2).build();

        Recurrence editada = serie.conPlantilla(cuenta, categoria, new BigDecimal("49900"), "Netflix premium");

        assertThat(editada.accountId()).isEqualTo(cuenta);
        assertThat(editada.categoryId()).isEqualTo(categoria);
        assertThat(editada.amount()).isEqualByComparingTo("49900");
        assertThat(editada.description()).isEqualTo("Netflix premium");
        assertThat(editada.rule()).isEqualTo(serie.rule());
        assertThat(editada.generatedCount()).isEqualTo(2);
    }
}
