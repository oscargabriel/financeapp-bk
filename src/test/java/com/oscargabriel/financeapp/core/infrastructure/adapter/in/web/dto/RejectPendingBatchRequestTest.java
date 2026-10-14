package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.oscargabriel.financeapp.compartido.support.Violaciones;
import com.oscargabriel.financeapp.core.support.TransactionMother;

class RejectPendingBatchRequestTest {

    private static final String GASTO = TransactionMother.GASTO_GUARDADO_ID.toString();
    private static final String TRANSFERENCIA = TransactionMother.TRANSFERENCIA_GUARDADA_ID.toString();

    @Test
    void unLoteDeIdsDistintosNoTieneViolaciones() {
        assertThat(Violaciones.de(new RejectPendingBatchRequest(List.of(GASTO, TRANSFERENCIA)))).isEmpty();
    }

    @Test
    void losIdsSonObligatorios() {
        assertThat(Violaciones.de(new RejectPendingBatchRequest(null)))
                .containsExactly(Map.entry("ids", List.of("Los ids son obligatorios")));
    }

    @Test
    void unLoteVacioNoSirve() {
        assertThat(Violaciones.de(new RejectPendingBatchRequest(List.of())))
                .containsExactly(Map.entry("ids", List.of("El lote lleva entre 1 y 100 ids")));
    }

    @Test
    void cienEsElTope() {
        List<String> cien = IntStream.range(0, 100).mapToObj(RejectPendingBatchRequestTest::uuid).toList();
        List<String> cientoUno = IntStream.range(0, 101).mapToObj(RejectPendingBatchRequestTest::uuid).toList();

        assertThat(Violaciones.de(new RejectPendingBatchRequest(cien))).isEmpty();
        assertThat(Violaciones.de(new RejectPendingBatchRequest(cientoUno)))
                .containsExactly(Map.entry("ids", List.of("El lote lleva entre 1 y 100 ids")));
    }

    /** El mismo id en otra caja es el mismo UUID. */
    @Test
    void unIdRepetidoEsUnErrorDelLote() {
        assertThat(Violaciones.de(new RejectPendingBatchRequest(List.of(GASTO, TRANSFERENCIA, GASTO.toUpperCase()))))
                .containsExactly(Map.entry("ids", List.of("Cada id va una sola vez")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "50000000-0000-7000-8000-00000000000g", "  "})
    void unIdQueNoEsUuidSaleEnSuPosicion(String malo) {
        assertThat(Violaciones.de(new RejectPendingBatchRequest(List.of(GASTO, malo))))
                .containsOnlyKeys("ids[1].<list element>");
    }

    @Test
    void unIdNuloSaleEnSuPosicion() {
        assertThat(Violaciones.de(new RejectPendingBatchRequest(Arrays.asList((String) null))))
                .containsExactly(Map.entry("ids[0].<list element>", List.of("Cada id es obligatorio")));
    }

    private static String uuid(int i) {
        return "0199ffff-0000-7000-8000-%012d".formatted(i);
    }
}
