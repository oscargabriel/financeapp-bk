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

    @Test
    void unaCategoriaCompletaNoTieneViolaciones() {
        assertThat(Violaciones.de(new CreateCategoryRequest("Plantas", "EXPENSE", "sprout", "#7CB342"))).isEmpty();
    }

    @Test
    void elIconoYElColorSonOpcionales() {
        assertThat(Violaciones.de(new CreateCategoryRequest("Bonos", "INCOME", null, null))).isEmpty();
    }

    /** En blanco no son un error de formato: el caso de uso los guarda como null. */
    @Test
    void elIconoYElColorEnBlancoNoTienenViolaciones() {
        assertThat(Violaciones.de(new CreateCategoryRequest("Bonos", "INCOME", " ", ""))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"expense", " Income ", "BOTH"})
    void elAlcanceNoDistingueMayusculasNiEspaciosDelBorde(String alcance) {
        assertThat(Violaciones.de(new CreateCategoryRequest("Plantas", alcance, null, null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#7cb342", " #7CB342 ", "#000000"})
    void aceptaElColorHexadecimalEnCualquierCaja(String color) {
        assertThat(Violaciones.de(new CreateCategoryRequest("Plantas", "EXPENSE", null, color))).isEmpty();
    }

    @Test
    void aceptaUnNombreDeSesentaCaracteres() {
        assertThat(Violaciones.de(new CreateCategoryRequest("x".repeat(60), "EXPENSE", null, null))).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("sin nombre", alta(null, "EXPENSE", null, null), "name", "El nombre es obligatorio"),
                Arguments.of("nombre en blanco", alta("   ", "EXPENSE", null, null), "name",
                        "El nombre es obligatorio"),
                Arguments.of("nombre de 61", alta("x".repeat(61), "EXPENSE", null, null), "name",
                        "El nombre no puede superar los 60 caracteres"),
                Arguments.of("sin alcance", alta("Plantas", null, null, null), "appliesTo",
                        "appliesTo es obligatorio"),
                Arguments.of("alcance en blanco", alta("Plantas", " ", null, null), "appliesTo",
                        "appliesTo es obligatorio"),
                Arguments.of("alcance desconocido", alta("Plantas", "GASTO", null, null), "appliesTo", ALCANCE),
                Arguments.of("icono de 41", alta("Plantas", "EXPENSE", "x".repeat(41), null), "icon",
                        "El icono no puede superar los 40 caracteres"),
                Arguments.of("color con nombre", alta("Plantas", "EXPENSE", null, "rojo"), "color",
                        "El color debe tener la forma #RRGGBB"),
                Arguments.of("color de cinco digitos", alta("Plantas", "EXPENSE", null, "#12345"), "color",
                        "El color debe tener la forma #RRGGBB"),
                Arguments.of("color sin numeral", alta("Plantas", "EXPENSE", null, "7CB342"), "color",
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
                .containsExactlyInAnyOrder("name", "appliesTo", "color");
    }

    private static CreateCategoryRequest alta(String nombre, String alcance, String icono, String color) {
        return new CreateCategoryRequest(nombre, alcance, icono, color);
    }
}
