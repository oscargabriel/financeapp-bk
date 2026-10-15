package com.oscargabriel.financeapp.core.domain.model;

import java.time.Instant;

/**
 * La cuenta Google de un ID token ya verificado (FIN-52): el sub que la identifica y lo que el
 * registro copia de ella. {@code emitidoEn} es el iat del token: la definicion de la primera contrasena
 * exige un token reciente (FIN-53).
 */
public record GoogleIdentity(String sub, String email, String firstName, String lastName, Instant emitidoEn) {

    /** users.first_name y last_name son VARCHAR(100). */
    private static final int LARGO_DEL_NOMBRE = 100;

    /**
     * El correo se normaliza como el alta. El nombre es obligatorio en users y Google no siempre trae
     * nombre de pila: sin el, el nombre completo, y sin ninguno, la parte del correo anterior a la @.
     */
    public static GoogleIdentity desdeClaims(String sub, String email, String givenName, String name,
            String familyName, Instant emitidoEn) {
        String correo = email.trim().toLowerCase();
        String nombre = presente(givenName) != null ? presente(givenName)
                : presente(name) != null ? presente(name)
                : correo.substring(0, correo.indexOf('@'));
        return new GoogleIdentity(sub, correo, recortar(nombre), recortar(presente(familyName)), emitidoEn);
    }

    private static String presente(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String recortar(String valor) {
        return valor == null || valor.length() <= LARGO_DEL_NOMBRE ? valor : valor.substring(0, LARGO_DEL_NOMBRE);
    }
}
