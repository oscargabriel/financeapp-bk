package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.oscargabriel.financeapp.compartido.support.Violaciones;

class UpdateUserProfileRequestTest {

    private static final String MENSAJE_CELULAR =
            "El celular va en formato internacional: + e indicativo, de 7 a 15 digitos en total, sin espacios ni separadores";

    @Test
    void unParcheCompletoNoTieneViolaciones() {
        assertThat(Violaciones.de(new UpdateUserProfileRequest(" Ana Maria ", "Perez", " Ana@Correo.com ",
                "+573001234567", "Europe/Madrid", " usd ", "unaClaveLarga"))).isEmpty();
    }

    @Test
    void todosLosCamposSonOpcionales() {
        assertThat(Violaciones.de(vacio())).isEmpty();
    }

    /** El blanco es la forma de borrar los opcionales: no es un error de formato. */
    @Test
    void elApellidoYElCelularEnBlancoSonValidos() {
        assertThat(Violaciones.de(new UpdateUserProfileRequest(null, "  ", null, "", null, null, null))).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("nombre en blanco", conNombre("   "),
                        "firstName", "El nombre no puede ir en blanco: para no cambiarlo, omitelo"),
                Arguments.of("nombre de 101", conNombre("x".repeat(101)),
                        "firstName", "El nombre no puede superar los 100 caracteres"),
                Arguments.of("apellido de 101", new UpdateUserProfileRequest(null, "x".repeat(101), null, null, null, null, null),
                        "lastName", "El apellido no puede superar los 100 caracteres"),
                Arguments.of("correo en blanco", conCorreo("  "),
                        "email", "El correo no puede ir en blanco: para no cambiarlo, omitelo"),
                Arguments.of("correo sin arroba", conCorreo("sin-arroba"),
                        "email", "El correo no tiene un formato valido"),
                Arguments.of("correo de 256", conCorreo("a".repeat(250) + "@x.com"),
                        "email", "El correo no tiene un formato valido"),
                Arguments.of("celular con guion", new UpdateUserProfileRequest(null, null, null, "12-34", null, null, null),
                        "phone", MENSAJE_CELULAR),
                Arguments.of("celular sin indicativo", new UpdateUserProfileRequest(null, null, null, "3109876543", null, null, null),
                        "phone", MENSAJE_CELULAR),
                Arguments.of("zona en blanco", conZona(" "),
                        "timezone", "La zona horaria no puede ir en blanco: para no cambiarla, omitela"),
                Arguments.of("zona desconocida", conZona("Marte/Base"),
                        "timezone", "La zona horaria no es una zona IANA conocida"),
                Arguments.of("moneda en blanco", conMoneda("  "),
                        "baseCurrencyCode", "La moneda no puede ir en blanco: para no cambiarla, omitela"),
                Arguments.of("moneda de dos letras", conMoneda("US"),
                        "baseCurrencyCode", "La moneda debe ser un codigo de tres letras"),
                Arguments.of("moneda con un digito", conMoneda("U5D"),
                        "baseCurrencyCode", "La moneda debe ser un codigo de tres letras"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void reportaUnSoloErrorEnElCampoInvalido(String caso, UpdateUserProfileRequest parche, String campo, String mensaje) {
        assertThat(Violaciones.de(parche)).containsExactly(Map.entry(campo, List.of(mensaje)));
    }

    @Test
    void unParcheSinCamposModificablesNoCambiaNada() {
        assertThat(vacio().sinCambios()).isTrue();
        assertThat(new UpdateUserProfileRequest(null, null, null, null, null, null, "unaClaveLarga").sinCambios())
                .isTrue();
    }

    @Test
    void cualquierCampoModificableCuentaComoCambio() {
        assertThat(new UpdateUserProfileRequest(null, null, null, "", null, null, null).sinCambios()).isFalse();
        assertThat(conZona("Europe/Madrid").sinCambios()).isFalse();
        assertThat(conMoneda("USD").sinCambios()).isFalse();
    }

    private static UpdateUserProfileRequest vacio() {
        return new UpdateUserProfileRequest(null, null, null, null, null, null, null);
    }

    private static UpdateUserProfileRequest conNombre(String nombre) {
        return new UpdateUserProfileRequest(nombre, null, null, null, null, null, null);
    }

    private static UpdateUserProfileRequest conCorreo(String correo) {
        return new UpdateUserProfileRequest(null, null, correo, null, null, null, null);
    }

    private static UpdateUserProfileRequest conZona(String zona) {
        return new UpdateUserProfileRequest(null, null, null, null, zona, null, null);
    }

    private static UpdateUserProfileRequest conMoneda(String moneda) {
        return new UpdateUserProfileRequest(null, null, null, null, null, moneda, null);
    }
}
