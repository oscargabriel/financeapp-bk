package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.validation;

/**
 * Expresiones de @Pattern compartidas por los requests. Todas aceptan el valor vacio: de exigir el
 * campo se encarga @NotBlank, y asi un campo vacio da un solo error y no tambien el de formato. Los
 * espacios del borde se toleran porque el caso de uso recorta antes de guardar.
 */
public final class Formatos {

    /** El mismo patron que el CHECK ck_users_email, para no aceptar aqui lo que la base rechaza. */
    public static final String EMAIL = "^\\s*$|^\\s*[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}\\s*$";

    public static final String CODIGO_MONEDA = "^\\s*([A-Za-z]{3})?\\s*$";

    /** Vacio, o al menos 8 caracteres de cualquier tipo, espacios incluidos. */
    public static final String AL_MENOS_8 = "^\\s*$|^[\\s\\S]{8,}$";

    private Formatos() {
    }
}
