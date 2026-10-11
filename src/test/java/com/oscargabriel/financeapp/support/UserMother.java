package com.oscargabriel.financeapp.support;

import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.domain.model.UserProfile;

/** Altas de usuario para los tests. El comando base es valido y completo. */
public final class UserMother {

    public static final String EMAIL = "ana@ejemplo.com";
    public static final String PASSWORD = "unaClaveLarga";
    public static final String HASH = "$2a$10$noEsUnHashReal";

    public static final UUID ID = UUID.fromString("10000000-0000-7000-8000-000000000001");

    private UserMother() {
    }

    /** El perfil tal como lo guarda la base: correo en minusculas, sin apellido y con celular. */
    public static UserProfile unPerfil() {
        return new UserProfile(ID, EMAIL, "Ana", null, "3001234567", "COP", "America/Bogota");
    }

    public static RegistrationCommand unAlta() {
        return new RegistrationCommand(EMAIL, PASSWORD, "Ana", "Gomez", "USD", "America/Lima", null);
    }

    public static RegistrationCommand unAltaConCelular(String celular) {
        return new RegistrationCommand(EMAIL, PASSWORD, "Ana", "Gomez", "USD", "America/Lima", celular);
    }

    /** Sin los dos campos opcionales: el caso de uso tiene que completarlos. */
    public static RegistrationCommand unAltaSinPreferencias() {
        return new RegistrationCommand(EMAIL, PASSWORD, "Ana", null, null, null, null);
    }
}
