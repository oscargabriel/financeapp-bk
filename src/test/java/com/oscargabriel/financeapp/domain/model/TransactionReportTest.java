package com.oscargabriel.financeapp.domain.model;

import static com.oscargabriel.financeapp.support.ReportMother.AHORA;
import static com.oscargabriel.financeapp.support.ReportMother.CUENTA_ID;
import static com.oscargabriel.financeapp.support.ReportMother.DESTINO_ID;
import static com.oscargabriel.financeapp.support.ReportMother.MERCADO_ID;
import static com.oscargabriel.financeapp.support.ReportMother.RESTAURANTES_ID;
import static com.oscargabriel.financeapp.support.ReportMother.conCuentas;
import static com.oscargabriel.financeapp.support.ReportMother.conTipos;
import static com.oscargabriel.financeapp.support.ReportMother.sinFiltros;
import static com.oscargabriel.financeapp.support.ReportMother.unGasto;
import static com.oscargabriel.financeapp.support.ReportMother.unIngreso;
import static com.oscargabriel.financeapp.support.ReportMother.unaTransferenciaEnDolares;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.oscargabriel.financeapp.domain.model.TransactionReport.CategoryTotal;
import com.oscargabriel.financeapp.domain.model.TransactionReport.TypeTotal;

class TransactionReportTest {

    private static final List<ReportedTransaction> DEL_MES = List.of(
            unGasto(MERCADO_ID, "Mercado", "85000.0000", "2026-09-02T15:00:00Z"),
            unIngreso("4500000.0000", "2026-09-01T14:00:00Z"),
            unGasto(RESTAURANTES_ID, "Restaurantes", "38000.0000", "2026-09-04T18:00:00Z"),
            unaTransferenciaEnDolares("2026-09-10T20:00:00Z"),
            unGasto(MERCADO_ID, "Mercado", "42500.0000", "2026-09-12T23:00:00Z"));

    @Test
    void totalizaLosTresTiposEnElOrdenDelEnumSumandoElMontoBase() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), DEL_MES, AHORA);

        assertThat(reporte.totalsByType())
                .extracting(TypeTotal::type, TypeTotal::total, TypeTotal::count)
                .containsExactly(
                        tuple(TransactionType.EXPENSE, new BigDecimal("165500.0000"), 3L),
                        tuple(TransactionType.INCOME, new BigDecimal("4500000.0000"), 1L),
                        tuple(TransactionType.TRANSFER, new BigDecimal("410000.0000"), 1L));
    }

    @Test
    void sinMovimientosLosTresTiposVanEnCeroYNoHayTotalesPorCategoria() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), List.of(), AHORA);

        assertThat(reporte.totalsByType())
                .extracting(TypeTotal::type, TypeTotal::total, TypeTotal::count)
                .containsExactly(
                        tuple(TransactionType.EXPENSE, BigDecimal.ZERO, 0L),
                        tuple(TransactionType.INCOME, BigDecimal.ZERO, 0L),
                        tuple(TransactionType.TRANSFER, BigDecimal.ZERO, 0L));
        assertThat(reporte.totalsByCategory()).isEmpty();
        assertThat(reporte.transactions()).isEmpty();
    }

    @Test
    void conFiltroDeTipoSoloTotalizaLosTiposPedidosEnElOrdenDelEnum() {
        TransactionReport reporte = TransactionReport.of("COP",
                conTipos(TransactionType.TRANSFER, TransactionType.EXPENSE), List.of(), AHORA);

        assertThat(reporte.totalsByType()).extracting(TypeTotal::type)
                .containsExactly(TransactionType.EXPENSE, TransactionType.TRANSFER);
    }

    @Test
    void losTotalesPorCategoriaDejanFueraLasTransferenciasYVanDeMayorAMenor() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), DEL_MES, AHORA);

        assertThat(reporte.totalsByCategory())
                .extracting(CategoryTotal::categoryName, CategoryTotal::total, CategoryTotal::count)
                .containsExactly(
                        tuple("Salario", new BigDecimal("4500000.0000"), 1L),
                        tuple("Mercado", new BigDecimal("127500.0000"), 2L),
                        tuple("Restaurantes", new BigDecimal("38000.0000"), 1L));
    }

    @Test
    void ordenaLosMovimientosDelMasRecienteAlMasAntiguo() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), DEL_MES, AHORA);

        assertThat(reporte.transactions()).hasSize(5).extracting(ReportedTransaction::occurredAt)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void elFiltroRechazaUnRangoInvertido() {
        assertThatThrownBy(() -> new TransactionReportFilter(
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), Set.of(), Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-09-30");
    }

    @Test
    void unSoloDiaEsUnRangoValidoYLosFiltrosNulosQuedanVacios() {
        LocalDate dia = LocalDate.of(2026, 9, 15);

        TransactionReportFilter filtro = new TransactionReportFilter(dia, dia, null, null, null);

        assertThat(filtro.categoryIds()).isEmpty();
        assertThat(filtro.accountIds()).isEmpty();
        assertThat(filtro.types()).isEmpty();
    }

    @Test
    void elNetoEsIngresosMenosGastosSinLasTransferencias() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), DEL_MES, AHORA);

        assertThat(reporte.net()).isEqualByComparingTo("4334500");
    }

    @Test
    void conSoloGastosElNetoSaleNegativo() {
        TransactionReport reporte = TransactionReport.of("COP", conTipos(TransactionType.EXPENSE),
                DEL_MES.stream().filter(t -> t.type() == TransactionType.EXPENSE).toList(), AHORA);

        assertThat(reporte.net()).isEqualByComparingTo("-165500");
    }

    @Test
    void conSoloTransferenciasElNetoEsCero() {
        TransactionReport reporte = TransactionReport.of("COP", conTipos(TransactionType.TRANSFER),
                List.of(unaTransferenciaEnDolares("2026-09-10T20:00:00Z")), AHORA);

        assertThat(reporte.net()).isEqualByComparingTo("0");
    }

    /** El gasto de 42.500 del dia 12 queda despues del instante del reporte: esta programado (FA-106). */
    private static final Instant EL_DIA_10 = Instant.parse("2026-09-10T21:00:00Z");

    @Test
    void losProgramadosSiguenEnLaListaPeroNoEnLosTotales() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), DEL_MES, EL_DIA_10);

        assertThat(reporte.transactions()).hasSize(5);
        assertThat(reporte.totalsByType())
                .extracting(TypeTotal::type, TypeTotal::total, TypeTotal::count)
                .containsExactly(
                        tuple(TransactionType.EXPENSE, new BigDecimal("123000.0000"), 2L),
                        tuple(TransactionType.INCOME, new BigDecimal("4500000.0000"), 1L),
                        tuple(TransactionType.TRANSFER, new BigDecimal("410000.0000"), 1L));
        assertThat(reporte.totalsByCategory())
                .extracting(CategoryTotal::categoryName, CategoryTotal::total, CategoryTotal::count)
                .contains(tuple("Mercado", new BigDecimal("85000.0000"), 1L));
        assertThat(reporte.net()).isEqualByComparingTo("4377000");
    }

    @Test
    void conSoloProgramadosLosTotalesVanEnCero() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), DEL_MES,
                Instant.parse("2026-08-31T00:00:00Z"));

        assertThat(reporte.transactions()).hasSize(5);
        assertThat(reporte.totalsByType()).extracting(TypeTotal::total, TypeTotal::count)
                .containsOnly(tuple(BigDecimal.ZERO, 0L));
        assertThat(reporte.totalsByCategory()).isEmpty();
        assertThat(reporte.net()).isEqualByComparingTo("0");
    }

    @Test
    void unMovimientoJustoEnElInstanteDelReporteYaCuenta() {
        Instant instante = Instant.parse("2026-09-12T23:00:00Z");

        TransactionReport reporte = TransactionReport.of("COP", conTipos(TransactionType.EXPENSE),
                List.of(unGasto(MERCADO_ID, "Mercado", "42500.0000", "2026-09-12T23:00:00Z")), instante);

        assertThat(reporte.scheduled(reporte.transactions().getFirst())).isFalse();
        assertThat(reporte.net()).isEqualByComparingTo("-42500");
    }

    @Test
    void marcaComoProgramadoLoQueOcurreDespuesDelInstanteDelReporte() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), DEL_MES, EL_DIA_10);

        assertThat(reporte.transactions())
                .filteredOn(reporte::scheduled)
                .extracting(ReportedTransaction::occurredAt)
                .containsExactly(Instant.parse("2026-09-12T23:00:00Z"));
        assertThat(reporte.asOf()).isEqualTo(EL_DIA_10);
    }

    @Test
    void elFiltroConservaLasCuentasPedidas() {
        assertThat(conCuentas(CUENTA_ID, DESTINO_ID).accountIds()).containsExactlyInAnyOrder(CUENTA_ID, DESTINO_ID);
    }
}
