package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import static com.oscargabriel.financeapp.support.InstallmentMother.unCuerpo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.oscargabriel.financeapp.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.support.InstallmentMother;
import com.oscargabriel.financeapp.support.Violaciones;

/**
 * El formato del alta y de la simulacion. Que la tarjeta sea del usuario y tenga corte y pago, y que la
 * fecha no sea futura en su zona, los decide el caso de uso.
 */
class CreateInstallmentPurchaseRequestTest {

    @Test
    void unaCompraCompletaNoTieneViolaciones() {
        assertThat(Violaciones.de(unCuerpo().request())).isEmpty();
    }

    @Test
    void losLimitesSeAdmiten() {
        assertThat(Violaciones.de(unCuerpo().installmentCount(1).request())).isEmpty();
        assertThat(Violaciones.de(unCuerpo().installmentCount(48).request())).isEmpty();
        assertThat(Violaciones.de(unCuerpo().amount(new BigDecimal("48")).installmentCount(48).request())).isEmpty();
        assertThat(Violaciones.de(unCuerpo().description("x".repeat(255)).request())).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("sin cuenta", unCuerpo().accountId(" "), "accountId", "La cuenta es obligatoria"),
                Arguments.of("sin categoria", unCuerpo().categoryId(null), "categoryId", "La categoria es obligatoria"),
                Arguments.of("sin monto", unCuerpo().amount(null), "amount", "El monto es obligatorio"),
                Arguments.of("monto en cero", unCuerpo().amount(BigDecimal.ZERO), "amount",
                        "El monto debe ser mayor que cero"),
                Arguments.of("monto con cinco decimales", unCuerpo().amount(new BigDecimal("1.12345")), "amount",
                        "El monto admite hasta 4 decimales y menos de 14 digitos enteros"),
                Arguments.of("monto menor que las cuotas", unCuerpo().amount(new BigDecimal("10")).installmentCount(12),
                        "amount", "El monto tiene que alcanzar para al menos 1 peso de capital por cuota"),
                Arguments.of("sin descripcion", unCuerpo().description("  "), "description",
                        "La descripcion es obligatoria"),
                Arguments.of("descripcion de 256", unCuerpo().description("x".repeat(256)), "description",
                        "La descripcion no puede superar los 255 caracteres"),
                Arguments.of("sin fecha", unCuerpo().purchaseDate(null), "purchaseDate",
                        "La fecha de compra es obligatoria"),
                Arguments.of("fecha con hora", unCuerpo().purchaseDate("2026-10-09T10:00:00-05:00"), "purchaseDate",
                        "La fecha debe ser YYYY-MM-DD, por ejemplo 2026-10-15"),
                Arguments.of("sin cuotas", unCuerpo().installmentCount(null), "installmentCount",
                        "El numero de cuotas es obligatorio"),
                Arguments.of("cero cuotas", unCuerpo().installmentCount(0), "installmentCount",
                        "Las cuotas van de 1 a 48"),
                Arguments.of("49 cuotas", unCuerpo().installmentCount(49), "installmentCount",
                        "Las cuotas van de 1 a 48"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoInvalidoConSuTexto(String caso, InstallmentMother.Cuerpo cuerpo, String campo, String texto) {
        assertThat(Violaciones.de(cuerpo.request())).containsExactly(Map.entry(campo, List.of(texto)));
    }

    @Test
    void sinMontoYConCeroCuotasReportaLosDos() {
        assertThat(Violaciones.de(unCuerpo().amount(null).installmentCount(0).request()).keySet())
                .containsExactlyInAnyOrder("amount", "installmentCount");
    }

    @Test
    void conCuotasFueraDeRangoNoJuzgaElMontoContraEllas() {
        assertThat(Violaciones.de(unCuerpo().amount(new BigDecimal("10")).installmentCount(49).request()).keySet())
                .containsExactly("installmentCount");
    }

    @Test
    void convierteAlComando() {
        CreateInstallmentPurchaseCommand compra = unCuerpo().purchaseDate(" 2026-10-09 ").request().toCommand();

        assertThat(compra.accountId()).isEqualTo(InstallmentMother.VISA_ID.toString());
        assertThat(compra.amount()).isEqualByComparingTo("1200000");
        assertThat(compra.purchaseDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(compra.installmentCount()).isEqualTo(3);
    }
}