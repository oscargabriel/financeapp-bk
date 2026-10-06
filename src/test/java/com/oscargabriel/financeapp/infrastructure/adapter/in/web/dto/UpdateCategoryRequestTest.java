package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.oscargabriel.financeapp.support.Violaciones;

class UpdateCategoryRequestTest {

    @Test
    void unParcheCompletoNoTieneViolaciones() {
        assertThat(Violaciones.de(new UpdateCategoryRequest("Huerta", "both", "leaf", "#558b2f"))).isEmpty();
    }

    @Test
    void todosLosCamposSonOpcionales() {
        assertThat(Violaciones.de(new UpdateCategoryRequest(null, null, null, null))).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("nombre en blanco", parche("   ", null, null, null), "name",
                        "El nombre no puede ir en blanco: para no cambiarlo, omitelo"),
                Arguments.of("nombre de 61", parche("x".repeat(61), null, null, null), "name",
                        "El nombre no puede superar los 60 caracteres"),
                Arguments.of("alcance en blanco", parche(null, " ", null, null), "appliesTo",
                        "appliesTo no puede ir en blanco: para no cambiarlo, omitelo"),
                Arguments.of("alcance desconocido", parche(null, "GASTO", null, null), "appliesTo",
                        "appliesTo debe ser EXPENSE, INCOME o BOTH"),
                Arguments.of("icono vacio", parche(null, null, "", null), "icon",
                        "El icono no puede ir en blanco: para no cambiarlo, omitelo"),
                Arguments.of("icono de 41", parche(null, null, "x".repeat(41), null), "icon",
                        "El icono no puede superar los 40 caracteres"),
                Arguments.of("color en blanco", parche(null, null, null, "  "), "color",
                        "El color no puede ir en blanco: para no cambiarlo, omitelo"),
                Arguments.of("color con nombre", parche(null, null, null, "rojo"), "color",
                        "El color debe tener la forma #RRGGBB"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoInvalidoConSuTexto(String caso, UpdateCategoryRequest request, String campo, String texto) {
        assertThat(Violaciones.de(request)).containsExactly(Map.entry(campo, List.of(texto)));
    }

    @Test
    void reportaTodosLosCamposInvalidosEnUnaSolaRespuesta() {
        assertThat(Violaciones.de(parche(" ", "GASTO", "", "rojo")).keySet())
                .containsExactlyInAnyOrder("name", "appliesTo", "icon", "color");
    }

    @Test
    void unParcheConTodoEnNullNoTraeCambios() {
        assertThat(parche(null, null, null, null).sinCambios()).isTrue();
    }

    @Test
    void unSoloCampoYaEsUnCambio() {
        assertThat(parche(null, null, null, "#000000").sinCambios()).isFalse();
    }

    private static UpdateCategoryRequest parche(String nombre, String alcance, String icono, String color) {
        return new UpdateCategoryRequest(nombre, alcance, icono, color);
    }
}
