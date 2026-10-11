package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.oscargabriel.financeapp.support.Violaciones;

class CreateCategoryRequestTest {

    private static final String ALCANCE = "appliesTo debe ser EXPENSE, INCOME o BOTH";

    private static final String ICONO = "sprout";

    private static final String COLOR = "#7CB342";

    @Test
    void unaCategoriaCompletaNoTieneViolaciones() {
        assertThat(Violaciones.de(new CreateCategoryRequest("Plantas", "EXPENSE", "sprout", "#7CB342"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"expense", " Income ", "BOTH"})
    void elAlcanceNoDistingueMayusculasNiEspaciosDelBorde(String alcance) {
        assertThat(Violaciones.de(new CreateCategoryRequest("Plantas", alcance, "sprout", "#7CB342"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#7cb342", " #7CB342 ", "#000000"})
    void aceptaElColorHexadecimalEnCualquierCaja(String color) {
        assertThat(Violaciones.de(new CreateCategoryRequest("Plantas", "EXPENSE", "sprout", color))).isEmpty();
    }

    @Test
    void aceptaUnNombreDeSesentaCaracteres() {
        assertThat(Violaciones.de(new CreateCategoryRequest("x".repeat(60), "EXPENSE", "sprout", "#7CB342"))).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("sin nombre", alta(null, "EXPENSE", ICONO, COLOR), "name", "El nombre es obligatorio"),
                Arguments.of("nombre en blanco", alta("   ", "EXPENSE", ICONO, COLOR), "name",
                        "El nombre es obligatorio"),
                Arguments.of("nombre de 61", alta("x".repeat(61), "EXPENSE", ICONO, COLOR), "name",
                        "El nombre no puede superar los 60 caracteres"),
                Arguments.of("sin alcance", alta("Plantas", null, ICONO, COLOR), "appliesTo",
                        "appliesTo es obligatorio"),
                Arguments.of("alcance en blanco", alta("Plantas", " ", ICONO, COLOR), "appliesTo",
                        "appliesTo es obligatorio"),
                Arguments.of("alcance desconocido", alta("Plantas", "GASTO", ICONO, COLOR), "appliesTo", ALCANCE),
                Arguments.of("sin icono", alta("Plantas", "EXPENSE", null, COLOR), "icon", "El icono es obligatorio"),
                Arguments.of("icono en blanco", alta("Plantas", "EXPENSE", " ", COLOR), "icon",
                        "El icono es obligatorio"),
                Arguments.of("icono de 41", alta("Plantas", "EXPENSE", "x".repeat(41), COLOR), "icon",
                        "El icono no puede superar los 40 caracteres"),
                Arguments.of("sin color", alta("Plantas", "EXPENSE", ICONO, null), "color", "El color es obligatorio"),
                Arguments.of("color vacio", alta("Plantas", "EXPENSE", ICONO, ""), "color", "El color es obligatorio"),
                Arguments.of("color con nombre", alta("Plantas", "EXPENSE", ICONO, "rojo"), "color",
                        "El color debe tener la forma #RRGGBB"),
                Arguments.of("color de cinco digitos", alta("Plantas", "EXPENSE", ICONO, "#12345"), "color",
                        "El color debe tener la forma #RRGGBB"),
                Arguments.of("color sin numeral", alta("Plantas", "EXPENSE", ICONO, "7CB342"), "color",
                        "El color debe tener la forma #RRGGBB"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoInvalidoConSuTexto(String caso, CreateCategoryRequest request, String campo, String texto) {
        assertThat(Violaciones.de(request)).containsExactly(Map.entry(campo, List.of(texto)));
    }

    @Test
    void reportaTodosLosCamposInvalidosEnUnaSolaRespuesta() {
        assertThat(Violaciones.de(alta(null, "GASTO", null, "rojo")).keySet())
                .containsExactlyInAnyOrder("name", "appliesTo", "icon", "color");
    }

    private static CreateCategoryRequest alta(String nombre, String alcance, String icono, String color) {
        return new CreateCategoryRequest(nombre, alcance, icono, color);
    }
}
