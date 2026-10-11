package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import static com.oscargabriel.financeapp.support.TransactionMother.unParche;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.oscargabriel.financeapp.support.TransactionMother;
import com.oscargabriel.financeapp.support.Violaciones;

/**
 * El formato de cada campo del parche. Las reglas de la transferencia no van aqui: dependen del
 * movimiento guardado y las decide el caso de uso.
 */
class UpdateTransactionRequestTest {

    private static final String FECHA_CON_OFFSET =
            "La fecha debe ser ISO-8601 con offset, por ejemplo 2026-09-20T10:15:00-05:00";

    @Test
    void ningunCampoEsObligatorio() {
        assertThat(Violaciones.de(unParche().request())).isEmpty();
    }

    @Test
    void unParcheConTodosLosCamposValidosNoTieneViolaciones() {
        assertThat(Violaciones.de(unParche().type("transfer").accountId(TransactionMother.ORIGEN_ID.toString())
                .destinationAccountId(TransactionMother.DESTINO_ID.toString())
                .categoryId(TransactionMother.MERCADO_ID.toString()).amount(new BigDecimal("45000.1234"))
                .description("x".repeat(255)).occurredAt(TransactionMother.FECHA)
                .destinationAmount(new BigDecimal("95.5")).request())).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("tipo desconocido", unParche().type("PAGO"), "type",
                        "El tipo debe ser EXPENSE, INCOME o TRANSFER"),
                Arguments.of("tipo en blanco", unParche().type("  "), "type",
                        "El tipo no puede ir en blanco: para no cambiarlo, omitelo"),
                Arguments.of("monto en cero", unParche().amount(BigDecimal.ZERO), "amount",
                        "El monto debe ser mayor que cero: el signo lo da el tipo"),
                Arguments.of("monto negativo", unParche().amount(new BigDecimal("-1")), "amount",
                        "El monto debe ser mayor que cero: el signo lo da el tipo"),
                Arguments.of("monto con cinco decimales", unParche().amount(new BigDecimal("10.12345")), "amount",
                        "El monto admite hasta 4 decimales y menos de 14 digitos enteros"),
                Arguments.of("monto de destino en cero", unParche().destinationAmount(BigDecimal.ZERO),
                        "destinationAmount", "El monto de destino debe ser mayor que cero"),
                Arguments.of("monto de destino con cinco decimales",
                        unParche().destinationAmount(new BigDecimal("10.12345")), "destinationAmount",
                        "El monto de destino admite hasta 4 decimales y menos de 14 digitos enteros"),
                Arguments.of("descripcion en blanco", unParche().description("   "), "description",
                        "La descripcion no puede ir en blanco: para no cambiarla, omitela"),
                Arguments.of("descripcion de 256", unParche().description("x".repeat(256)), "description",
                        "La descripcion no puede superar los 255 caracteres"),
                Arguments.of("fecha en blanco", unParche().occurredAt(" "), "occurredAt",
                        "La fecha no puede ir en blanco: para no cambiarla, omitela"),
                Arguments.of("fecha sin offset", unParche().occurredAt("2026-09-21T10:00:00"), "occurredAt",
                        FECHA_CON_OFFSET),
                Arguments.of("fecha que no es fecha", unParche().occurredAt("ayer"), "occurredAt",
                        FECHA_CON_OFFSET));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoInvalidoConSuTexto(String caso, TransactionMother.Parche parche, String campo, String texto) {
        assertThat(Violaciones.de(parche.request())).containsExactly(Map.entry(campo, List.of(texto)));
    }

    @Test
    void reportaTodosLosCamposInvalidosJuntos() {
        assertThat(Violaciones.de(unParche().amount(new BigDecimal("-1")).occurredAt("ayer").request()).keySet())
                .containsExactlyInAnyOrder("amount", "occurredAt");
    }

    @Test
    void unParcheSinCamposNoTraeCambios() {
        assertThat(unParche().request().sinCambios()).isTrue();
    }

    @Test
    void unSoloCampoYaEsUnCambio() {
        assertThat(unParche().description("Fruta").request().sinCambios()).isFalse();
        assertThat(unParche().amount(BigDecimal.ONE).request().sinCambios()).isFalse();
        assertThat(unParche().categoryId("x").request().sinCambios()).isFalse();
        assertThat(unParche().destinationAmount(BigDecimal.TEN).request().sinCambios()).isFalse();
    }
}
