package com.oscargabriel.financeapp.support;

import com.oscargabriel.financeapp.domain.model.RegistrationCommand;

/** Altas de usuario para los tests. El comando base es valido y completo. */
public final class UserMother {

    public static final String EMAIL = "ana@ejemplo.com";
    public static final String PASSWORD = "unaClaveLarga";
    public static final String HASH = "$2a$10$noEsUnHashReal";

    private UserMother() {
    }

    public static RegistrationCommand unAlta() {
        return new RegistrationCommand(EMAIL, PASSWORD, "Ana", "Gomez", "USD", "America/Lima");
    }

    /** Sin los dos campos opcionales: el caso de uso tiene que completarlos. */
    public static RegistrationCommand unAltaSinPreferencias() {
        return new RegistrationCommand(EMAIL, PASSWORD, "Ana", null, null, null);
    }
}
