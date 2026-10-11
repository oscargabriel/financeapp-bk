package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.oscargabriel.financeapp.support.UserMother;
import com.oscargabriel.financeapp.support.Violaciones;

class RegisterUserRequestTest {

    @Test
    void unAltaCompletaNoTieneViolaciones() {
        assertThat(Violaciones.de(valido())).isEmpty();
    }

    /** Moneda y zona son opcionales: el caso de uso las completa con el DEFAULT de la tabla. */
    @Test
    void lasPreferenciasVaciasSonValidas() {
        assertThat(Violaciones.de(new RegisterUserRequest(
                UserMother.EMAIL, UserMother.PASSWORD, "Ana", null, "  ", "", null))).isEmpty();
    }

    @Test
    void reportaTodosLosCamposInvalidosConElTextoDeCadaRegla() {
        Map<String, List<String>> violaciones = Violaciones.de(new RegisterUserRequest(
                "no-es-un-email", "corta", "  ", "x".repeat(101), "US", "Marte/Olympus", null));

        assertThat(violaciones).containsExactlyInAnyOrderEntriesOf(Map.of(
                "email", List.of("El correo no tiene un formato valido"),
                "password", List.of("La contrasena debe tener al menos 8 caracteres"),
                "firstName", List.of("El nombre es obligatorio"),
                "lastName", List.of("El apellido no puede superar los 100 caracteres"),
                "baseCurrencyCode", List.of("La moneda debe ser un codigo de tres letras"),
                "timezone", List.of("La zona horaria no es una zona IANA conocida")));
    }

    /** Un campo vacio da un solo error, el de obligatorio: no tambien el de formato. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void unCampoObligatorioVacioDaUnSoloError(String vacio) {
        Map<String, List<String>> violaciones = Violaciones.de(new RegisterUserRequest(
                vacio, vacio, vacio, null, null, null, null));

        assertThat(violaciones).containsExactlyInAnyOrderEntriesOf(Map.of(
                "email", List.of("El correo es obligatorio"),
                "password", List.of("La contrasena es obligatoria"),
                "firstName", List.of("El nombre es obligatorio")));
    }

    @Test
    void rechazaUnCorreoDeMasDe255Caracteres() {
        String largo = "a".repeat(250) + "@x.com";

        assertThat(Violaciones.de(conEmail(largo)))
                .containsExactly(Map.entry("email", List.of("El correo no tiene un formato valido")));
    }

    /** Los espacios del borde se recortan al guardar: no hacen invalido el correo. */
    @Test
    void aceptaElCorreoConEspaciosEnLosBordes() {
        assertThat(Violaciones.de(conEmail("  Ana@Ejemplo.COM  "))).isEmpty();
    }

    /**
     * BCrypt trunca en silencio a partir de 72 bytes: sin el tope, dos claves que compartan el
     * prefijo entrarian como la misma.
     */
    @Test
    void rechazaLaClaveQueSuperaElLimiteDeBcrypt() {
        assertThat(Violaciones.de(conPassword("a".repeat(73))))
                .containsExactly(Map.entry("password", List.of("La contrasena no puede superar los 72 bytes")));
    }

    /** El tope es en bytes: 37 enes son 74 bytes en UTF-8 aunque sean 37 caracteres. */
    @Test
    void elTopeDeLaClaveSeMideEnBytesYNoEnCaracteres() {
        assertThat(Violaciones.de(conPassword("ñ".repeat(37)))).containsKey("password");
        assertThat(Violaciones.de(conPassword("ñ".repeat(36)))).isEmpty();
    }

    @Test
    void rechazaUnNombreDeMasDe100Caracteres() {
        assertThat(Violaciones.de(new RegisterUserRequest(
                UserMother.EMAIL, UserMother.PASSWORD, "x".repeat(101), null, null, null, null)))
                .containsExactly(Map.entry("firstName", List.of("El nombre no puede superar los 100 caracteres")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", " COP ", "EUR"})
    void aceptaCualquierCodigoDeTresLetras(String moneda) {
        assertThat(Violaciones.de(new RegisterUserRequest(
                UserMother.EMAIL, UserMother.PASSWORD, "Ana", null, moneda, null, null))).isEmpty();
    }

    /** El celular es opcional, y los espacios del borde se recortan al guardar. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" +573001234567 ", "3001234567", "1234567", "123456789012345"})
    void aceptaUnCelularValidoOAusente(String celular) {
        assertThat(Violaciones.de(conCelular(celular))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"300 123", "abc1234567", "123456", "1234567890123456", "12-34", "57+3001234567"})
    void rechazaUnCelularMalFormado(String celular) {
        assertThat(Violaciones.de(conCelular(celular))).containsExactly(Map.entry("phone",
                List.of("El celular admite un + opcional y de 7 a 15 digitos, sin espacios ni separadores")));
    }

    private static RegisterUserRequest valido() {
        return new RegisterUserRequest(UserMother.EMAIL, UserMother.PASSWORD, "Ana", "Gomez", "USD",
                "America/Lima", null);
    }

    private static RegisterUserRequest conEmail(String email) {
        return new RegisterUserRequest(email, UserMother.PASSWORD, "Ana", null, null, null, null);
    }

    private static RegisterUserRequest conCelular(String celular) {
        return new RegisterUserRequest(UserMother.EMAIL, UserMother.PASSWORD, "Ana", null, null, null, celular);
    }

    private static RegisterUserRequest conPassword(String password) {
        return new RegisterUserRequest(UserMother.EMAIL, password, "Ana", null, null, null, null);
    }
}
