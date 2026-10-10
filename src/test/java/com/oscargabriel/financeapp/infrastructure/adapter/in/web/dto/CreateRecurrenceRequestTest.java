package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import static com.oscargabriel.financeapp.support.RecurrenceMother.unCuerpo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.oscargabriel.financeapp.domain.model.CreateRecurrenceCommand;
import com.oscargabriel.financeapp.domain.model.Frequency;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.support.RecurrenceMother;
import com.oscargabriel.financeapp.support.Violaciones;

/**
 * El formato del alta y la forma de la regla. Que la cuenta y la categoria sean del usuario, y el tope
 * de ocurrencias, los decide el caso de uso.
 */
class CreateRecurrenceRequestTest {

    private static final String FECHA = "La fecha debe ser YYYY-MM-DD, por ejemplo 2026-10-15";

    @Test
    void unAltaMensualCompletaNoTieneViolaciones() {
        assertThat(Violaciones.de(unCuerpo().request())).isEmpty();
    }

    @Test
    void unAltaSemanalSinFinYSinIntervalNoTieneViolaciones() {
        assertThat(Violaciones.de(unCuerpo().type("income").frequency("weekly").dayOfMonth(null)
                .dayOfWeek("friday").occurrences(null).request())).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("sin tipo", unCuerpo().type(null), "type", "El tipo es obligatorio"),
                Arguments.of("tipo desconocido", unCuerpo().type("PAGO"), "type", "El tipo debe ser EXPENSE o INCOME"),
                Arguments.of("transferencia", unCuerpo().type("TRANSFER"), "type",
                        "Una serie solo puede ser de gastos o de ingresos"),
                Arguments.of("sin cuenta", unCuerpo().accountId(" "), "accountId", "La cuenta es obligatoria"),
                Arguments.of("sin categoria", unCuerpo().categoryId(null), "categoryId",
                        "La categoria es obligatoria"),
                Arguments.of("sin monto", unCuerpo().amount(null), "amount", "El monto es obligatorio"),
                Arguments.of("monto en cero", unCuerpo().amount(BigDecimal.ZERO), "amount",
                        "El monto debe ser mayor que cero: el signo lo da el tipo"),
                Arguments.of("monto con cinco decimales", unCuerpo().amount(new BigDecimal("1.12345")), "amount",
                        "El monto admite hasta 4 decimales y menos de 14 digitos enteros"),
                Arguments.of("sin descripcion", unCuerpo().description("  "), "description",
                        "La descripcion es obligatoria"),
                Arguments.of("descripcion de 256", unCuerpo().description("x".repeat(256)), "description",
                        "La descripcion no puede superar los 255 caracteres"),
                Arguments.of("sin frecuencia", unCuerpo().frequency(null), "frequency", "La frecuencia es obligatoria"),
                Arguments.of("frecuencia desconocida", unCuerpo().frequency("DAILY"), "frequency",
                        "La frecuencia debe ser WEEKLY o MONTHLY"),
                Arguments.of("intervalo cero", unCuerpo().interval(0), "interval", "El intervalo es al menos 1"),
                Arguments.of("mensual cada 13", unCuerpo().interval(13), "interval",
                        "Una serie mensual se repite como mucho cada 12 meses: anual es 12"),
                Arguments.of("dia del mes 32", unCuerpo().dayOfMonth(32), "dayOfMonth", "El dia del mes va de 1 a 31"),
                Arguments.of("mensual sin dia", unCuerpo().dayOfMonth(null), "dayOfMonth",
                        "Una serie mensual necesita dayOfMonth"),
                Arguments.of("dia de la semana en mensual", unCuerpo().dayOfWeek("MONDAY"), "dayOfWeek",
                        "Solo una serie semanal lleva dayOfWeek"),
                Arguments.of("dia de la semana desconocido", unCuerpo().frequency("WEEKLY").dayOfMonth(null)
                        .dayOfWeek("LUNES"), "dayOfWeek", "El dia de la semana debe ser MONDAY a SUNDAY, en ingles"),
                Arguments.of("sin inicio", unCuerpo().startDate(null), "startDate",
                        "La fecha de inicio es obligatoria"),
                Arguments.of("inicio con hora", unCuerpo().startDate("2026-10-15T10:00:00-05:00"), "startDate", FECHA),
                Arguments.of("fin que no es fecha", unCuerpo().occurrences(null).endDate("pronto"), "endDate", FECHA),
                Arguments.of("fin antes del inicio", unCuerpo().occurrences(null).endDate("2026-10-14"), "endDate",
                        "La fecha de fin no puede ser anterior a la de inicio"),
                Arguments.of("fin y repeticiones", unCuerpo().endDate("2026-12-31"), "occurrences",
                        "Una serie termina por fecha o por numero de repeticiones, no por los dos"),
                Arguments.of("cero repeticiones", unCuerpo().occurrences(0), "occurrences",
                        "Las repeticiones van de 1 a 500"),
                Arguments.of("501 repeticiones", unCuerpo().occurrences(501), "occurrences",
                        "Las repeticiones van de 1 a 500"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoInvalidoConSuTexto(String caso, RecurrenceMother.Cuerpo cuerpo, String campo, String texto) {
        assertThat(Violaciones.de(cuerpo.request())).containsExactly(Map.entry(campo, List.of(texto)));
    }

    @Test
    void semanalSinDiaDeLaSemanaYConDiaDelMesReportaLosDos() {
        assertThat(Violaciones.de(unCuerpo().frequency("WEEKLY").request())).containsOnly(
                Map.entry("dayOfWeek", List.of("Una serie semanal necesita dayOfWeek")),
                Map.entry("dayOfMonth", List.of("Solo una serie mensual lleva dayOfMonth")));
    }

    @Test
    void reportaTodosLosCamposInvalidosJuntos() {
        assertThat(Violaciones.de(unCuerpo().amount(null).interval(0).request()).keySet())
                .containsExactlyInAnyOrder("amount", "interval");
    }

    @Test
    void conviertePlanoAlComandoConIntervalUnoPorDefecto() {
        CreateRecurrenceCommand alta = unCuerpo().type(" income ").frequency("weekly").dayOfWeek("friday")
                .dayOfMonth(null).startDate(" 2026-10-16 ").occurrences(null).endDate("2026-12-31").request()
                .toCommand();

        assertThat(alta.type()).isEqualTo(TransactionType.INCOME);
        assertThat(alta.frequency()).isEqualTo(Frequency.WEEKLY);
        assertThat(alta.interval()).isEqualTo(1);
        assertThat(alta.dayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(alta.dayOfMonth()).isNull();
        assertThat(alta.startDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(alta.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(alta.occurrences()).isNull();
    }

    @Test
    void unFinEnBlancoEsSinFin() {
        assertThat(unCuerpo().occurrences(null).endDate(" ").request().toCommand().endDate()).isNull();
    }
}
