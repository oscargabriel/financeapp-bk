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

class UpdateUserProfileRequestTest {

    @Test
    void unParcheCompletoNoTieneViolaciones() {
        assertThat(Violaciones.de(new UpdateUserProfileRequest(" Ana Maria ", "Perez", " Ana@Correo.com ",
                "+573001234567", "Europe/Madrid", "unaClaveLarga"))).isEmpty();
    }

    @Test
    void todosLosCamposSonOpcionales() {
        assertThat(Violaciones.de(vacio())).isEmpty();
    }

    /** El blanco es la forma de borrar los opcionales: no es un error de formato. */
    @Test
    void elApellidoYElCelularEnBlancoSonValidos() {
        assertThat(Violaciones.de(new UpdateUserProfileRequest(null, "  ", null, "", null, null))).isEmpty();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("nombre en blanco", new UpdateUserProfileRequest("   ", null, null, null, null, null),
                        "firstName", "El nombre no puede ir en blanco: para no cambiarlo, omitelo"),
                Arguments.of("nombre de 101", new UpdateUserProfileRequest("x".repeat(101), null, null, null, null, null),
                        "firstName", "El nombre no puede superar los 100 caracteres"),
                Arguments.of("apellido de 101", new UpdateUserProfileRequest(null, "x".repeat(101), null, null, null, null),
                        "lastName", "El apellido no puede superar los 100 caracteres"),
                Arguments.of("correo en blanco", new UpdateUserProfileRequest(null, null, "  ", null, null, null),
                        "email", "El correo no puede ir en blanco: para no cambiarlo, omitelo"),
                Arguments.of("correo sin arroba", new UpdateUserProfileRequest(null, null, "sin-arroba", null, null, null),
                        "email", "El correo no tiene un formato valido"),
                Arguments.of("correo de 256", new UpdateUserProfileRequest(null, null, "a".repeat(250) + "@x.com", null, null, null),
                        "email", "El correo no tiene un formato valido"),
                Arguments.of("celular con guion", new UpdateUserProfileRequest(null, null, null, "12-34", null, null),
                        "phone", "El celular admite un + opcional y de 7 a 15 digitos, sin espacios ni separadores"),
                Arguments.of("zona en blanco", new UpdateUserProfileRequest(null, null, null, null, " ", null),
                        "timezone", "La zona horaria no puede ir en blanco: para no cambiarla, omitela"),
                Arguments.of("zona desconocida", new UpdateUserProfileRequest(null, null, null, null, "Marte/Base", null),
                        "timezone", "La zona horaria no es una zona IANA conocida"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void reportaUnSoloErrorEnElCampoInvalido(String caso, UpdateUserProfileRequest parche, String campo, String mensaje) {
        assertThat(Violaciones.de(parche)).containsExactly(Map.entry(campo, List.of(mensaje)));
    }

    @Test
    void unParcheSinCamposModificablesNoCambiaNada() {
        assertThat(vacio().sinCambios()).isTrue();
        assertThat(new UpdateUserProfileRequest(null, null, null, null, null, "unaClaveLarga").sinCambios()).isTrue();
    }

    @Test
    void cualquierCampoModificableCuentaComoCambio() {
        assertThat(new UpdateUserProfileRequest(null, null, null, "", null, null).sinCambios()).isFalse();
        assertThat(new UpdateUserProfileRequest(null, null, null, null, "Europe/Madrid", null).sinCambios()).isFalse();
    }

    private static UpdateUserProfileRequest vacio() {
        return new UpdateUserProfileRequest(null, null, null, null, null, null);
    }
}
