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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.oscargabriel.financeapp.support.TransactionMother;
import com.oscargabriel.financeapp.support.Violaciones;

/**
 * Las reglas que se deciden mirando solo el elemento. Las que necesitan las cuentas y categorias del
 * usuario (que existan, sean suyas, esten activas, con el alcance correcto) y la moneda contra el catalogo son
 * del caso de uso.
 */
class CreateTransactionRequestTest {

    private static final String MONTO_FUERA_DE_RANGO =
            "El monto admite hasta 4 decimales y menos de 14 digitos enteros";

    @Test
    void unGastoUnIngresoYUnaTransferenciaCompletosNoTienenViolaciones() {
        assertThat(Violaciones.de(TransactionMother.unGasto().request())).isEmpty();
        assertThat(Violaciones.de(TransactionMother.unIngreso().request())).isEmpty();
        assertThat(Violaciones.de(TransactionMother.unaTransferencia().request())).isEmpty();
    }

    @Test
    void aceptaNotasDeMilCaracteresYUnaMonedaDeTresLetrasEnCualquierCaja() {
        assertThat(Violaciones.de(TransactionMother.unGasto().notes("x".repeat(1000)).currencyCode(" usd ")
                .request())).isEmpty();
    }

    /** Entre monedas distintas, lo que entra al destino (FA-51); que las cuentas difieran lo decide el caso de uso. */
    @Test
    void aceptaUnaTransferenciaConMontoDeDestino() {
        assertThat(Violaciones.de(TransactionMother.unaTransferencia().destinationAmount(new BigDecimal("95.5"))
                .request())).isEmpty();
    }

    /** Sin fecha no es un error: el caso de uso le pone el instante de la peticion (FA-60). */
    @ParameterizedTest(name = "occurredAt = [{0}]")
    @NullSource
    @ValueSource(strings = {"", "   "})
    void aceptaUnElementoSinFecha(String fecha) {
        assertThat(Violaciones.de(TransactionMother.unGasto().occurredAt(fecha).request())).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        String origen = TransactionMother.ORIGEN_ID.toString();
        String mercado = TransactionMother.MERCADO_ID.toString();
        return Stream.of(
                Arguments.of("sin tipo", TransactionMother.unGasto().type(null), "type", "El tipo es obligatorio"),
                Arguments.of("tipo desconocido", TransactionMother.unGasto().type("REFUND"), "type",
                        "El tipo debe ser EXPENSE, INCOME o TRANSFER"),
                Arguments.of("sin monto", TransactionMother.unGasto().amount(null), "amount", "El monto es obligatorio"),
                Arguments.of("monto en cero", TransactionMother.unGasto().amount(BigDecimal.ZERO), "amount",
                        "El monto debe ser mayor que cero: el signo lo da el tipo"),
                Arguments.of("monto negativo", TransactionMother.unGasto().amount(new BigDecimal("-1")), "amount",
                        "El monto debe ser mayor que cero: el signo lo da el tipo"),
                Arguments.of("monto con cinco decimales", TransactionMother.unGasto().amount(new BigDecimal("1.00001")),
                        "amount", MONTO_FUERA_DE_RANGO),
                Arguments.of("monto que desborda NUMERIC(18,4)",
                        TransactionMother.unGasto().amount(new BigDecimal("100000000000000")), "amount",
                        MONTO_FUERA_DE_RANGO),
                Arguments.of("sin cuenta", TransactionMother.unGasto().accountId(null), "accountId",
                        "La cuenta es obligatoria"),
                Arguments.of("gasto sin categoria", TransactionMother.unGasto().categoryId(null), "categoryId",
                        "La categoria es obligatoria en un gasto o un ingreso"),
                Arguments.of("gasto con cuenta destino",
                        TransactionMother.unGasto().destinationAccountId(TransactionMother.DESTINO_ID.toString()),
                        "destinationAccountId", "Solo una transferencia lleva cuenta destino"),
                Arguments.of("transferencia sin destino", TransactionMother.unaTransferencia().destinationAccountId(null),
                        "destinationAccountId", "La cuenta destino es obligatoria"),
                Arguments.of("transferencia a la misma cuenta",
                        TransactionMother.unaTransferencia().destinationAccountId(origen), "destinationAccountId",
                        "La cuenta destino tiene que ser distinta de la de origen"),
                Arguments.of("transferencia con categoria", TransactionMother.unaTransferencia().categoryId(mercado),
                        "categoryId", "Una transferencia no lleva categoria"),
                Arguments.of("gasto con monto de destino", TransactionMother.unGasto().destinationAmount(BigDecimal.TEN),
                        "destinationAmount", "Solo una transferencia lleva monto de destino"),
                Arguments.of("monto de destino en cero",
                        TransactionMother.unaTransferencia().destinationAmount(BigDecimal.ZERO), "destinationAmount",
                        "El monto de destino debe ser mayor que cero"),
                Arguments.of("monto de destino con cinco decimales",
                        TransactionMother.unaTransferencia().destinationAmount(new BigDecimal("1.00001")),
                        "destinationAmount", "El monto de destino admite hasta 4 decimales y menos de 14 digitos enteros"),
                Arguments.of("moneda que no es un codigo de 3 letras", TransactionMother.unGasto().currencyCode("DOLAR"),
                        "currencyCode", "La moneda debe ser un codigo ISO 4217 de 3 letras"),
                Arguments.of("sin descripcion", TransactionMother.unGasto().description(null), "description",
                        "La descripcion es obligatoria"),
                Arguments.of("descripcion en blanco", TransactionMother.unGasto().description("  "), "description",
                        "La descripcion es obligatoria"),
                Arguments.of("descripcion de 256", TransactionMother.unGasto().description("x".repeat(256)),
                        "description", "La descripcion no puede superar los 255 caracteres"),
                Arguments.of("notas de 1001", TransactionMother.unGasto().notes("x".repeat(1001)), "notes",
                        "Las notas no pueden superar los 1000 caracteres"),
                Arguments.of("fecha sin offset", TransactionMother.unGasto().occurredAt("2026-09-20T10:15:00"),
                        "occurredAt", "La fecha debe ser ISO-8601 con offset, por ejemplo 2026-09-20T10:15:00-05:00"),
                Arguments.of("fecha que no es fecha", TransactionMother.unGasto().occurredAt("ayer"), "occurredAt",
                        "La fecha debe ser ISO-8601 con offset, por ejemplo 2026-09-20T10:15:00-05:00"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoInvalidoConSuTexto(String caso, TransactionMother.Elemento elemento, String campo,
            String texto) {
        assertThat(Violaciones.de(elemento.request())).containsExactly(Map.entry(campo, List.of(texto)));
    }

    /** Sin un tipo valido no se sabe si la categoria o el destino sobran: ya hay un error en type. */
    @Test
    void conElTipoInvalidoNoOpinaSobreCategoriaNiDestino() {
        assertThat(Violaciones.de(TransactionMother.unGasto().type("REFUND")
                .destinationAccountId(TransactionMother.DESTINO_ID.toString()).request()).keySet())
                .containsExactly("type");
    }

    @Test
    void reportaTodosLosCamposInvalidosDelElementoJuntos() {
        assertThat(Violaciones.de(TransactionMother.unGasto().amount(new BigDecimal("-5")).currencyCode("US")
                .description(" ").occurredAt("2026-09-21T10:00:00").request()).keySet())
                .containsExactlyInAnyOrder("amount", "currencyCode", "description", "occurredAt");
    }
}
