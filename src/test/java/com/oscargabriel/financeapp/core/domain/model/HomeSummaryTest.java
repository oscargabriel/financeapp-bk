package com.oscargabriel.financeapp.core.domain.model;

import static com.oscargabriel.financeapp.core.support.AccountMother.efectivo;
import static com.oscargabriel.financeapp.core.support.AccountMother.tarjeta;
import static com.oscargabriel.financeapp.core.support.AccountMother.visa;
import static com.oscargabriel.financeapp.core.support.BalanceMother.sumasDelEscenario;
import static com.oscargabriel.financeapp.core.support.MonthlySpendingMother.unMes;
import static com.oscargabriel.financeapp.core.support.ReportMother.MERCADO_ID;
import static com.oscargabriel.financeapp.core.support.ReportMother.unGasto;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

class HomeSummaryTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    private static HomeSummary conMeses(List<MonthlySpending> meses) {
        return HomeSummary.of(OCTUBRE, sumasDelEscenario(), meses, List.of(), List.of(), List.of());
    }

    private static HomeSummary conMasCostosos(List<ReportedTransaction> masCostosos) {
        return HomeSummary.of(OCTUBRE, sumasDelEscenario(), List.of(), List.of(), masCostosos, List.of());
    }

    private static BigDecimal cupoUsado(Account cuenta) {
        return HomeSummary.of(OCTUBRE, sumasDelEscenario(), List.of(), List.of(), List.of(), List.of(cuenta))
                .accounts().getFirst().creditUsedPercent();
    }

    @Test
    void rellenaLosSeisMesesDelMasAntiguoAlMasRecienteConLosHuecosEnCero() {
        HomeSummary resumen = conMeses(List.of(
                unMes(OCTUBRE, "1905500", "5300000"),
                unMes(YearMonth.of(2026, 9), "1820000", "4500000"),
                unMes(YearMonth.of(2026, 7), "1445000", "4500000")));

        assertThat(resumen.months()).extracting(HomeSummary.Month::periodMonth).containsExactly(
                YearMonth.of(2026, 5), YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8),
                YearMonth.of(2026, 9), OCTUBRE);
        assertThat(resumen.months()).extracting(HomeSummary.Month::totalSpent)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1445000"), BigDecimal.ZERO,
                        new BigDecimal("1820000"), new BigDecimal("1905500"));
        assertThat(resumen.months()).extracting(HomeSummary.Month::totalIncome)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("4500000"), BigDecimal.ZERO,
                        new BigDecimal("4500000"), new BigDecimal("5300000"));
    }

    @Test
    void sinMesesTraeLosSeisEnCeroYSinVariacion() {
        HomeSummary resumen = conMeses(List.of());

        assertThat(resumen.months()).hasSize(6).allSatisfy(m -> {
            assertThat(m.totalSpent()).isZero();
            assertThat(m.totalIncome()).isZero();
        });
        assertThat(resumen.spent()).isZero();
        assertThat(resumen.spentChangePercent()).isNull();
    }

    @Test
    void ignoraLosMesesFueraDeLaVentana() {
        HomeSummary resumen = conMeses(List.of(unMes(YearMonth.of(2026, 4), "999", "999")));

        assertThat(resumen.months()).allSatisfy(m -> assertThat(m.totalSpent()).isZero());
    }

    @Test
    void elGastadoEsElDelMesEnCursoYLaVariacionSaleConDosDecimales() {
        HomeSummary resumen = conMeses(List.of(
                unMes(OCTUBRE, "1905500", "0"),
                unMes(YearMonth.of(2026, 9), "1820000", "0")));

        assertThat(resumen.spent()).isEqualByComparingTo("1905500");
        assertThat(resumen.spentChangePercent()).isEqualTo(new BigDecimal("4.70"));
    }

    @Test
    void unGastoMenorQueElDelMesAnteriorDaVariacionNegativa() {
        HomeSummary resumen = conMeses(List.of(
                unMes(OCTUBRE, "500000", "0"),
                unMes(YearMonth.of(2026, 9), "1000000", "0")));

        assertThat(resumen.spentChangePercent()).isEqualTo(new BigDecimal("-50.00"));
    }

    @Test
    void sinGastosElMesAnteriorLaVariacionEsNull() {
        HomeSummary resumen = conMeses(List.of(
                unMes(OCTUBRE, "500000", "0"),
                unMes(YearMonth.of(2026, 9), "0", "4500000")));

        assertThat(resumen.spentChangePercent()).isNull();
    }

    @Test
    void llevaLaMonedaElMesElSaldoDebitoYLosUltimosGastos() {
        ReportedTransaction gasto = unGasto(MERCADO_ID, "Mercado", "50000", "2026-10-10T15:00:00Z");

        HomeSummary resumen = HomeSummary.of(OCTUBRE, sumasDelEscenario(), List.of(), List.of(gasto), List.of(),
                List.of());

        assertThat(resumen.currencyCode()).isEqualTo("COP");
        assertThat(resumen.month()).isEqualTo(OCTUBRE);
        assertThat(resumen.debitBalance()).isEqualByComparingTo("12375000");
        assertThat(resumen.latestExpenses()).containsExactly(gasto);
    }

    @Test
    void cadaGastoMasCostosoTraeSuProporcionFrenteAlMayor() {
        ReportedTransaction dolares = unGasto(MERCADO_ID, "Mercado", "205000", "2026-10-10T15:00:00Z");
        ReportedTransaction pesos = unGasto(MERCADO_ID, "Mercado", "150000", "2026-10-11T15:00:00Z");

        HomeSummary resumen = conMasCostosos(List.of(dolares, pesos));

        assertThat(resumen.topExpenses()).extracting(HomeSummary.TopExpense::expense).containsExactly(dolares, pesos);
        assertThat(resumen.topExpenses()).extracting(HomeSummary.TopExpense::shareOfTop)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("73.17"));
    }

    @Test
    void sinGastosEnElMesLosMasCostososVienenVacios() {
        assertThat(conMasCostosos(List.of()).topExpenses()).isEmpty();
    }

    @Test
    void unaTarjetaConDeudaUsaSuProporcionDelCupo() {
        assertThat(cupoUsado(visa())).isEqualTo(new BigDecimal("13.16"));
    }

    @Test
    void unaTarjetaConSaldoAFavorNoUsaCupo() {
        assertThat(cupoUsado(tarjeta("5000000", "20000"))).isZero();
    }

    @Test
    void unaTarjetaPasadaDelCupoSeQuedaEnCien() {
        assertThat(cupoUsado(tarjeta("1000000", "-1200000"))).isEqualByComparingTo("100");
    }

    @Test
    void unaTarjetaSinCupoOConCupoEnCeroNoTienePorcentaje() {
        assertThat(cupoUsado(tarjeta(null, "-10000"))).isNull();
        assertThat(cupoUsado(tarjeta("0", "-10000"))).isNull();
    }

    @Test
    void otroTipoDeCuentaNoTienePorcentaje() {
        assertThat(cupoUsado(efectivo())).isNull();
    }

    @Test
    void cadaCuentaConservaSusDatos() {
        HomeSummary resumen = HomeSummary.of(OCTUBRE, sumasDelEscenario(), List.of(), List.of(), List.of(),
                List.of(efectivo(), visa()));

        assertThat(resumen.accounts()).extracting(HomeSummary.AccountUsage::account).containsExactly(efectivo(), visa());
    }
}
