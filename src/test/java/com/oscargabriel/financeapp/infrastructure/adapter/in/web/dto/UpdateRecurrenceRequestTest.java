package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import static com.oscargabriel.financeapp.support.RecurrenceMother.unCuerpoDeParche;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.oscargabriel.financeapp.domain.model.Frequency;
import com.oscargabriel.financeapp.domain.model.GroupScope;
import com.oscargabriel.financeapp.domain.model.UpdateRecurrenceCommand;
import com.oscargabriel.financeapp.support.RecurrenceMother;
import com.oscargabriel.financeapp.support.TransactionMother;
import com.oscargabriel.financeapp.support.Violaciones;

/**
 * El formato de cada campo del parche. La forma de la regla resultante depende de la serie guardada, y
 * la decide el caso de uso; aqui solo el rango de cada valor suelto.
 */
class UpdateRecurrenceRequestTest {

    @Test
    void soloElAlcanceEsObligatorio() {
        assertThat(Violaciones.de(unCuerpoDeParche().request())).isEmpty();
    }

    @Test
    void unParcheConTodosLosCamposValidosNoTieneViolaciones() {
        assertThat(Violaciones.de(unCuerpoDeParche().scope("all")
                .accountId(TransactionMother.DESTINO_ID.toString()).categoryId(TransactionMother.MERCADO_ID.toString())
                .amount(new BigDecimal("35000.5")).description("x".repeat(255)).frequency("weekly").interval(52)
                .dayOfWeek("monday").dayOfMonth(31).request())).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("sin alcance", unCuerpoDeParche().scope(null), "scope",
                        "El alcance es obligatorio: FUTURE o ALL"),
                Arguments.of("alcance desconocido", unCuerpoDeParche().scope("PAST"), "scope",
                        "El alcance debe ser FUTURE o ALL"),
                Arguments.of("monto en cero", unCuerpoDeParche().amount(BigDecimal.ZERO), "amount",
                        "El monto debe ser mayor que cero: el signo lo da el tipo"),
                Arguments.of("descripcion en blanco", unCuerpoDeParche().description(" "), "description",
                        "La descripcion no puede ir en blanco: para no cambiarla, omitela"),
                Arguments.of("descripcion de 256", unCuerpoDeParche().description("x".repeat(256)), "description",
                        "La descripcion no puede superar los 255 caracteres"),
                Arguments.of("frecuencia desconocida", unCuerpoDeParche().frequency("DAILY"), "frequency",
                        "La frecuencia debe ser WEEKLY o MONTHLY"),
                Arguments.of("intervalo cero", unCuerpoDeParche().interval(0), "interval",
                        "El intervalo es al menos 1"),
                Arguments.of("dia de la semana desconocido", unCuerpoDeParche().dayOfWeek("LUNES"), "dayOfWeek",
                        "El dia de la semana debe ser MONDAY a SUNDAY, en ingles"),
                Arguments.of("dia del mes 32", unCuerpoDeParche().dayOfMonth(32), "dayOfMonth",
                        "El dia del mes va de 1 a 31"),
                Arguments.of("dia del mes 0", unCuerpoDeParche().dayOfMonth(0), "dayOfMonth",
                        "El dia del mes va de 1 a 31"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoInvalidoConSuTexto(String caso, RecurrenceMother.CuerpoDeParche parche, String campo,
            String texto) {
        assertThat(Violaciones.de(parche.request())).containsExactly(Map.entry(campo, List.of(texto)));
    }

    @Test
    void soloElAlcanceNoTraeCambios() {
        assertThat(unCuerpoDeParche().request().sinCambios()).isTrue();
        assertThat(unCuerpoDeParche().interval(2).request().sinCambios()).isFalse();
    }

    @Test
    void convierteAlComando() {
        UpdateRecurrenceCommand parche = unCuerpoDeParche().scope(" all ").frequency("weekly").dayOfWeek("friday")
                .request().toCommand();

        assertThat(parche.scope()).isEqualTo(GroupScope.ALL);
        assertThat(parche.frequency()).isEqualTo(Frequency.WEEKLY);
        assertThat(parche.dayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(parche.interval()).isNull();
        assertThat(parche.dayOfMonth()).isNull();
        assertThat(parche.amount()).isNull();
    }
}
