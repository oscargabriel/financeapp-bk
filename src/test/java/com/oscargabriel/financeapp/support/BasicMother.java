package com.oscargabriel.financeapp.support;

import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;

/**
 * Credenciales del Basic para los tests de la cadena de seguridad. Tienen que ser las mismas que
 * declara src/test/resources/application.yaml en spring.security.basic: la aplicacion construye su
 * unico usuario desde ahi, y si los dos valores dejan de coincidir todo Basic sale 401.
 */
public final class BasicMother {

    public static final String USUARIO = "monitor-de-pruebas";

    public static final String CLAVE = "clave-basic-de-pruebas";

    private BasicMother() {
    }

    public static Consumer<HttpHeaders> cabecera() {
        return headers -> headers.setBasicAuth(USUARIO, CLAVE);
    }

    /** El usuario existe y la clave no: prueba que la contrasena del entorno se verifica de verdad. */
    public static Consumer<HttpHeaders> cabeceraConClaveIncorrecta() {
        return headers -> headers.setBasicAuth(USUARIO, CLAVE + "-mal");
    }
}
