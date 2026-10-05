package com.oscargabriel.financeapp.domain.model;

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
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), DEL_MES);

        assertThat(reporte.totalsByType())
                .extracting(TypeTotal::type, TypeTotal::total, TypeTotal::count)
                .containsExactly(
                        tuple(TransactionType.EXPENSE, new BigDecimal("165500.0000"), 3L),
                        tuple(TransactionType.INCOME, new BigDecimal("4500000.0000"), 1L),
                        tuple(TransactionType.TRANSFER, new BigDecimal("410000.0000"), 1L));
    }

    @Test
    void sinMovimientosLosTresTiposVanEnCeroYNoHayTotalesPorCategoria() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), List.of());

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
                conTipos(TransactionType.TRANSFER, TransactionType.EXPENSE), List.of());

        assertThat(reporte.totalsByType()).extracting(TypeTotal::type)
                .containsExactly(TransactionType.EXPENSE, TransactionType.TRANSFER);
    }

    @Test
    void losTotalesPorCategoriaDejanFueraLasTransferenciasYVanDeMayorAMenor() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), DEL_MES);

        assertThat(reporte.totalsByCategory())
                .extracting(CategoryTotal::categoryName, CategoryTotal::total, CategoryTotal::count)
                .containsExactly(
                        tuple("Salario", new BigDecimal("4500000.0000"), 1L),
                        tuple("Mercado", new BigDecimal("127500.0000"), 2L),
                        tuple("Restaurantes", new BigDecimal("38000.0000"), 1L));
    }

    @Test
    void ordenaLosMovimientosDelMasRecienteAlMasAntiguo() {
        TransactionReport reporte = TransactionReport.of("COP", sinFiltros(), DEL_MES);

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
    void elFiltroConservaLasCuentasPedidas() {
        assertThat(conCuentas(CUENTA_ID, DESTINO_ID).accountIds()).containsExactlyInAnyOrder(CUENTA_ID, DESTINO_ID);
    }
}
