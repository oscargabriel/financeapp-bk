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
import org.junit.jupiter.params.provider.ValueSource;

import com.oscargabriel.financeapp.support.Violaciones;

class CreateAccountRequestTest {

    private static final String FUERA_DE_RANGO = " admite hasta 4 decimales y menos de 14 digitos enteros";

    private static final String TASA_FUERA =
            "La tasa de interes mensual va de 0 a 10, en porcentaje y con hasta 4 decimales";

    @Test
    void unEfectivoCompletoNoTieneViolaciones() {
        assertThat(Violaciones.de(efectivo())).isEmpty();
    }

    /** Una tarjeta arranca debiendo: el saldo inicial negativo es la deuda. */
    @Test
    void unaTarjetaCompletaNoTieneViolaciones() {
        assertThat(Violaciones.de(new CreateAccountRequest("Mastercard", "CREDIT", "COP",
                new BigDecimal("-200000"), new BigDecimal("3000000"), 20, 5, null, null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"cash", " Savings ", "OTHER"})
    void elTipoNoDistingueMayusculasNiEspaciosDelBorde(String tipo) {
        assertThat(Violaciones.de(alta("Billetera", tipo, "COP"))).isEmpty();
    }

    /** Ceros a la derecha no son decimales de verdad: 1.50000 cabe en NUMERIC(18,4). */
    @Test
    void losCerosALaDerechaNoCuentanComoDecimales() {
        assertThat(Violaciones.de(new CreateAccountRequest("Billetera", "CASH", "COP",
                new BigDecimal("1.50000"), null, null, null, null, null))).isEmpty();
    }

    /** Cero es una tarjeta sin interes; diez es el tope; sin tasa, la tarjeta no la tiene cargada. */
    @ParameterizedTest
    @ValueSource(strings = {"0", "10", "2.15", "1.50000"})
    void unaTarjetaAdmiteLaTasaDentroDelRango(String tasa) {
        assertThat(Violaciones.de(conTasa(new BigDecimal(tasa)))).isEmpty();
    }

    @Test
    void unaTarjetaSinTasaNoTieneViolaciones() {
        assertThat(Violaciones.de(tarjeta(new BigDecimal("1000"), 20, 5))).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("sin nombre", alta(null, "CASH", "COP"), "name", "El nombre es obligatorio"),
                Arguments.of("nombre en blanco", alta("   ", "CASH", "COP"), "name", "El nombre es obligatorio"),
                Arguments.of("nombre de 81", alta("x".repeat(81), "CASH", "COP"), "name",
                        "El nombre no puede superar los 80 caracteres"),
                Arguments.of("sin tipo", alta("Billetera", null, "COP"), "type", "El tipo es obligatorio"),
                Arguments.of("tipo desconocido", alta("Billetera", "WALLET", "COP"), "type",
                        "El tipo debe ser uno de CASH, DEBIT, CREDIT, SAVINGS, INVESTMENT u OTHER"),
                Arguments.of("sin moneda", alta("Billetera", "CASH", null), "currencyCode",
                        "La moneda es obligatoria"),
                Arguments.of("moneda de dos letras", alta("Billetera", "CASH", "CO"), "currencyCode",
                        "La moneda debe ser un codigo de tres letras"),
                Arguments.of("moneda con digito", alta("Billetera", "CASH", "C0P"), "currencyCode",
                        "La moneda debe ser un codigo de tres letras"),
                Arguments.of("saldo con cinco decimales", conSaldo(new BigDecimal("1.00001")), "initialBalance",
                        "El saldo inicial" + FUERA_DE_RANGO),
                Arguments.of("saldo que desborda NUMERIC(18,4)", conSaldo(new BigDecimal("100000000000000")),
                        "initialBalance", "El saldo inicial" + FUERA_DE_RANGO),
                Arguments.of("limite en cero", tarjeta(BigDecimal.ZERO, 20, 5), "creditLimit",
                        "El cupo debe ser mayor que cero y" + FUERA_DE_RANGO),
                Arguments.of("limite negativo", tarjeta(new BigDecimal("-1"), 20, 5), "creditLimit",
                        "El cupo debe ser mayor que cero y" + FUERA_DE_RANGO),
                Arguments.of("limite con cinco decimales", tarjeta(new BigDecimal("0.00001"), 20, 5),
                        "creditLimit", "El cupo debe ser mayor que cero y" + FUERA_DE_RANGO),
                Arguments.of("dia de corte 0", tarjeta(new BigDecimal("1000"), 0, 5), "statementDay",
                        "El dia de corte debe estar entre 1 y 31"),
                Arguments.of("dia de corte 32", tarjeta(new BigDecimal("1000"), 32, 5), "statementDay",
                        "El dia de corte debe estar entre 1 y 31"),
                Arguments.of("dia de pago 0", tarjeta(new BigDecimal("1000"), 20, 0), "paymentDueDay",
                        "El dia de pago debe estar entre 1 y 31"),
                Arguments.of("dia de pago 32", tarjeta(new BigDecimal("1000"), 20, 32), "paymentDueDay",
                        "El dia de pago debe estar entre 1 y 31"),
                Arguments.of("limite en un debito", new CreateAccountRequest("Debito", "DEBIT", "COP", null,
                        new BigDecimal("1000"), null, null, null, null), "creditLimit", "Solo una cuenta CREDIT tiene cupo"),
                Arguments.of("dia de corte en un debito", new CreateAccountRequest("Debito", "DEBIT", "COP", null,
                        null, 15, null, null, null), "statementDay", "Solo una cuenta CREDIT tiene dia de corte"),
                Arguments.of("dia de pago en un debito", new CreateAccountRequest("Debito", "DEBIT", "COP", null,
                        null, null, 5, null, null), "paymentDueDay", "Solo una cuenta CREDIT tiene dia de pago"),
                Arguments.of("tasa negativa", conTasa(new BigDecimal("-1")), "monthlyInterestRate", TASA_FUERA),
                Arguments.of("tasa mayor que diez", conTasa(new BigDecimal("10.5")), "monthlyInterestRate",
                        TASA_FUERA),
                Arguments.of("tasa con cinco decimales", conTasa(new BigDecimal("1.23456")), "monthlyInterestRate",
                        TASA_FUERA),
                Arguments.of("tasa fuera de rango en un debito", new CreateAccountRequest("Debito", "DEBIT", "COP",
                        null, null, null, null, new BigDecimal("50"), null), "monthlyInterestRate",
                        "Solo una cuenta CREDIT tiene tasa de interes"),
                Arguments.of("saldo vigente en el cuerpo",new CreateAccountRequest("Billetera", "CASH", "COP",
                        BigDecimal.TEN, null, null, null, null, new BigDecimal("999999")), "currentBalance",
                        "El saldo vigente lo calcula el sistema; envia initialBalance"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoInvalidoConSuTexto(String caso, CreateAccountRequest request, String campo, String texto) {
        assertThat(Violaciones.de(request)).containsExactly(Map.entry(campo, List.of(texto)));
    }

    @Test
    void reportaTodosLosCamposInvalidosEnUnaSolaRespuesta() {
        assertThat(Violaciones.de(new CreateAccountRequest(" ", "DEBIT", "pesos", null,
                new BigDecimal("1000"), 15, 40, null, BigDecimal.ONE)).keySet())
                .containsExactlyInAnyOrder("name", "currencyCode", "creditLimit", "statementDay",
                        "paymentDueDay", "currentBalance");
    }

    /** Con el tipo invalido no se sabe si los campos de credito sobran: ya hay un error en type. */
    @Test
    void conElTipoInvalidoNoOpinaSobreLosCamposDeCredito() {
        assertThat(Violaciones.de(new CreateAccountRequest("Billetera", "WALLET", "COP", null,
                new BigDecimal("1000"), 15, 5, null, null)).keySet())
                .containsExactly("type");
    }

    private static CreateAccountRequest efectivo() {
        return new CreateAccountRequest("Billetera", "CASH", "COP", new BigDecimal("150000"), null, null, null,
                null, null);
    }

    private static CreateAccountRequest alta(String nombre, String tipo, String moneda) {
        return new CreateAccountRequest(nombre, tipo, moneda, null, null, null, null, null, null);
    }

    private static CreateAccountRequest conSaldo(BigDecimal saldo) {
        return new CreateAccountRequest("Billetera", "CASH", "COP", saldo, null, null, null, null, null);
    }

    private static CreateAccountRequest tarjeta(BigDecimal limite, Integer corte, Integer pago) {
        return new CreateAccountRequest("Tarjeta", "CREDIT", "COP", null, limite, corte, pago, null, null);
    }

    private static CreateAccountRequest conTasa(BigDecimal tasa) {
        return new CreateAccountRequest("Tarjeta", "CREDIT", "COP", null, null, null, null, tasa, null);
    }
}
