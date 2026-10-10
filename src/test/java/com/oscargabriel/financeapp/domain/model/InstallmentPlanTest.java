package com.oscargabriel.financeapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class InstallmentPlanTest {

    private static final BigDecimal DOS = new BigDecimal("2");

    /** Visa de corte 20 y pago 5. */
    private static InstallmentPlan visa(String monto, int cuotas, BigDecimal tasa, LocalDate compra) {
        return new InstallmentPlan(new BigDecimal(monto), cuotas, tasa, compra, 20, 5);
    }

    private static List<LocalDate> vencimientos(InstallmentPlan plan) {
        return plan.cuotas().stream().map(InstallmentPlan.Cuota::dueDate).toList();
    }

    @Test
    void unaCompraAntesDelCorteVenceElPagoDelMesSiguiente() {
        InstallmentPlan plan = visa("1200000", 3, DOS, LocalDate.of(2026, 10, 9));

        assertThat(vencimientos(plan)).containsExactly(
                LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 5), LocalDate.of(2027, 1, 5));
    }

    @Test
    void unaCompraElMismoDiaDelCorteEntraEnEseCorte() {
        assertThat(visa("1200000", 1, DOS, LocalDate.of(2026, 10, 20)).cuotas().getFirst().dueDate())
                .isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void unaCompraDespuesDelCorteEntraEnElDelMesSiguiente() {
        assertThat(visa("1200000", 1, DOS, LocalDate.of(2026, 10, 21)).cuotas().getFirst().dueDate())
                .isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void conPagoPosteriorAlCorteVenceElMismoMesDelCorte() {
        InstallmentPlan plan = new InstallmentPlan(new BigDecimal("100000"), 2, DOS, LocalDate.of(2026, 10, 3), 5, 20);

        assertThat(vencimientos(plan)).containsExactly(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 20));
    }

    @Test
    void conPagoIgualAlCorteVenceElMesSiguiente() {
        InstallmentPlan plan = new InstallmentPlan(new BigDecimal("100000"), 1, DOS, LocalDate.of(2026, 10, 3), 15, 15);

        assertThat(vencimientos(plan)).containsExactly(LocalDate.of(2026, 11, 15));
    }

    @Test
    void elDia31CaeElUltimoDiaDelMesSinMoverLasSiguientes() {
        InstallmentPlan plan = new InstallmentPlan(new BigDecimal("800000"), 4, BigDecimal.ZERO,
                LocalDate.of(2026, 12, 15), 31, 31);

        assertThat(vencimientos(plan)).containsExactly(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28),
                LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30));
    }

    @Test
    void elCorteDe31EnUnMesCortoEsSuUltimoDia() {
        InstallmentPlan plan = new InstallmentPlan(new BigDecimal("100000"), 1, BigDecimal.ZERO,
                LocalDate.of(2027, 2, 28), 31, 10);

        assertThat(vencimientos(plan)).containsExactly(LocalDate.of(2027, 3, 10));
    }

    @Test
    void capitalFijoEInteresSobreElSaldoPendiente() {
        List<InstallmentPlan.Cuota> cuotas = visa("1200000", 3, DOS, LocalDate.of(2026, 10, 9)).cuotas();

        assertThat(cuotas).extracting(InstallmentPlan.Cuota::number).containsExactly(1, 2, 3);
        assertThat(cuotas).extracting(InstallmentPlan.Cuota::principal).containsExactly(
                new BigDecimal("400000"), new BigDecimal("400000"), new BigDecimal("400000"));
        assertThat(cuotas).extracting(InstallmentPlan.Cuota::interest).containsExactly(
                new BigDecimal("24000"), new BigDecimal("16000"), new BigDecimal("8000"));
        assertThat(cuotas).extracting(InstallmentPlan.Cuota::amount).containsExactly(
                new BigDecimal("424000"), new BigDecimal("416000"), new BigDecimal("408000"));
    }

    @Test
    void laUltimaSeLlevaElRestoDelCapitalYElInteresRedondeaLaMitadHaciaArriba() {
        List<InstallmentPlan.Cuota> cuotas = visa("1000000", 3, new BigDecimal("2.15"), LocalDate.of(2026, 10, 9))
                .cuotas();

        assertThat(cuotas).extracting(InstallmentPlan.Cuota::principal).containsExactly(
                new BigDecimal("333333"), new BigDecimal("333333"), new BigDecimal("333334"));
        assertThat(cuotas).extracting(InstallmentPlan.Cuota::interest).containsExactly(
                new BigDecimal("21500"), new BigDecimal("14333"), new BigDecimal("7167"));
    }

    @Test
    void elCapitalSumaExactamenteElTotalAunqueTraigaDecimales() {
        List<InstallmentPlan.Cuota> cuotas = visa("1000.5", 3, BigDecimal.ZERO, LocalDate.of(2026, 10, 9)).cuotas();

        assertThat(cuotas).extracting(InstallmentPlan.Cuota::principal).containsExactly(
                new BigDecimal("333"), new BigDecimal("333"), new BigDecimal("334.5"));
    }

    @Test
    void conMontoIgualAlNumeroDeCuotasCadaUnaLlevaUnPeso() {
        List<InstallmentPlan.Cuota> cuotas = visa("48", 48, DOS, LocalDate.of(2026, 10, 9)).cuotas();

        assertThat(cuotas).hasSize(48).allSatisfy(c -> assertThat(c.principal()).isEqualByComparingTo("1"));
    }

    @Test
    void unaCuotaNoLlevaInteres() {
        List<InstallmentPlan.Cuota> cuotas = visa("300000", 1, DOS, LocalDate.of(2026, 10, 9)).cuotas();

        assertThat(cuotas).singleElement().satisfies(c -> {
            assertThat(c.principal()).isEqualByComparingTo("300000");
            assertThat(c.interest()).isEqualByComparingTo("0");
            assertThat(c.amount()).isEqualByComparingTo("300000");
        });
    }

    @Test
    void conTasaCeroNoHayInteres() {
        assertThat(visa("900000", 3, BigDecimal.ZERO, LocalDate.of(2026, 10, 9)).cuotas())
                .allSatisfy(c -> assertThat(c.interest()).isEqualByComparingTo("0"));
    }

    @Test
    void totalesDelPlan() {
        InstallmentPlan plan = visa("1200000", 3, DOS, LocalDate.of(2026, 10, 9));

        assertThat(plan.totalInterest()).isEqualByComparingTo("48000");
        assertThat(plan.totalAmount()).isEqualByComparingTo("1248000");
    }

    @Test
    void cuentaLasVencidasHastaHoyIncluyendoLaDeHoy() {
        InstallmentPlan plan = visa("600000", 6, DOS, LocalDate.of(2026, 6, 10));

        assertThat(plan.vencidas(LocalDate.of(2026, 10, 4))).isEqualTo(3);
        assertThat(plan.vencidas(LocalDate.of(2026, 10, 5))).isEqualTo(4);
        assertThat(plan.vencidas(LocalDate.of(2026, 6, 10))).isZero();
    }
}
