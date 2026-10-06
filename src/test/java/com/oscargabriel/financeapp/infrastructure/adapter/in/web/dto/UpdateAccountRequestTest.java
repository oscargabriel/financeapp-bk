package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.oscargabriel.financeapp.domain.model.UpdateAccountCommand;
import com.oscargabriel.financeapp.support.Violaciones;

class UpdateAccountRequestTest {

    @Test
    void unParcheCompletoNoTieneViolaciones() {
        assertThat(Violaciones.de(new UpdateAccountRequest("Bolsillo", "usd", new BigDecimal("-200000.5"),
                new BigDecimal("4000000"), 1, 31, null, null, null))).isEmpty();
    }

    @Test
    void todosLosCamposSonOpcionales() {
        assertThat(Violaciones.de(vacio())).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("nombre en blanco", conNombre("   "), "name",
                        "El nombre no puede ir en blanco: para no cambiarlo, omitelo"),
                Arguments.of("nombre de 81", conNombre("x".repeat(81)), "name",
                        "El nombre no puede superar los 80 caracteres"),
                Arguments.of("moneda en blanco", conMoneda(" "), "currencyCode",
                        "La moneda no puede ir en blanco: para no cambiarla, omitela"),
                Arguments.of("moneda de dos letras", conMoneda("us"), "currencyCode",
                        "La moneda debe ser un codigo de tres letras"),
                Arguments.of("saldo con cinco decimales", conSaldoInicial(new BigDecimal("1.23456")), "initialBalance",
                        "El saldo inicial admite hasta 4 decimales y menos de 14 digitos enteros"),
                Arguments.of("cupo cero", conCupo(BigDecimal.ZERO), "creditLimit",
                        "El cupo debe ser mayor que cero"),
                Arguments.of("cupo que no cabe", conCupo(new BigDecimal("100000000000000")), "creditLimit",
                        "El cupo admite hasta 4 decimales y menos de 14 digitos enteros"),
                Arguments.of("dia de corte 0", conDias(0, null), "statementDay",
                        "El dia de corte debe estar entre 1 y 31"),
                Arguments.of("dia de corte 32", conDias(32, null), "statementDay",
                        "El dia de corte debe estar entre 1 y 31"),
                Arguments.of("dia de pago 0", conDias(null, 0), "paymentDueDay",
                        "El dia de pago debe estar entre 1 y 31"),
                Arguments.of("dia de pago 32", conDias(null, 32), "paymentDueDay",
                        "El dia de pago debe estar entre 1 y 31"),
                Arguments.of("saldo vigente", new UpdateAccountRequest(null, null, null, null, null, null,
                        BigDecimal.ONE, null, null), "currentBalance",
                        "El saldo vigente lo calcula el sistema; para corregirlo, cambia initialBalance"),
                Arguments.of("tipo", new UpdateAccountRequest(null, null, null, null, null, null, null, "CASH", null),
                        "type", "El tipo de una cuenta no se puede cambiar"),
                Arguments.of("estado", new UpdateAccountRequest(null, null, null, null, null, null, null, null, false),
                        "isActive", "El estado de la cuenta no se cambia con este endpoint"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoInvalidoConSuTexto(String caso, UpdateAccountRequest request, String campo, String texto) {
        assertThat(Violaciones.de(request)).containsExactly(Map.entry(campo, List.of(texto)));
    }

    @Test
    void reportaTodosLosCamposInvalidosEnUnaSolaRespuesta() {
        assertThat(Violaciones.de(new UpdateAccountRequest(" ", "us", new BigDecimal("1.23456"), BigDecimal.ZERO,
                32, 0, BigDecimal.ONE, "CASH", false)).keySet())
                .containsExactlyInAnyOrder("name", "currencyCode", "initialBalance", "creditLimit", "statementDay",
                        "paymentDueDay", "currentBalance", "type", "isActive");
    }

    @Test
    void unParcheConTodoEnNullNoTraeCambios() {
        assertThat(vacio().sinCambios()).isTrue();
    }

    @Test
    void unSoloCampoYaEsUnCambio() {
        assertThat(conDias(null, 12).sinCambios()).isFalse();
    }

    @Test
    void elComandoLlevaLosSeisCamposModificables() {
        UpdateAccountRequest request = new UpdateAccountRequest("Bolsillo", "usd", BigDecimal.TEN, BigDecimal.ONE,
                3, 4, null, null, null);

        assertThat(request.toCommand()).isEqualTo(new UpdateAccountCommand(
                "Bolsillo", "usd", BigDecimal.TEN, BigDecimal.ONE, 3, 4));
    }

    private static UpdateAccountRequest vacio() {
        return new UpdateAccountRequest(null, null, null, null, null, null, null, null, null);
    }

    private static UpdateAccountRequest conNombre(String nombre) {
        return new UpdateAccountRequest(nombre, null, null, null, null, null, null, null, null);
    }

    private static UpdateAccountRequest conMoneda(String moneda) {
        return new UpdateAccountRequest(null, moneda, null, null, null, null, null, null, null);
    }

    private static UpdateAccountRequest conSaldoInicial(BigDecimal saldo) {
        return new UpdateAccountRequest(null, null, saldo, null, null, null, null, null, null);
    }

    private static UpdateAccountRequest conCupo(BigDecimal cupo) {
        return new UpdateAccountRequest(null, null, null, cupo, null, null, null, null, null);
    }

    private static UpdateAccountRequest conDias(Integer corte, Integer pago) {
        return new UpdateAccountRequest(null, null, null, null, corte, pago, null, null, null);
    }
}
