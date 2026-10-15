package com.oscargabriel.financeapp.core.support;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;

/** Altas de usuario para los tests. El comando base es valido y completo. */
public final class UserMother {

    public static final String EMAIL = "ana@ejemplo.com";
    public static final String PASSWORD = "unaClaveLarga";
    public static final String HASH = "$2a$10$noEsUnHashReal";

    public static final UUID ID = UUID.fromString("10000000-0000-7000-8000-000000000001");

    private UserMother() {
    }

    /**
     * El perfil tal como lo guarda la base: correo en minusculas, sin apellido y con celular. Registrado
     * con contrasena y sin Google.
     */
    public static UserProfile unPerfil() {
        return new UserProfile(ID, EMAIL, "Ana", null, "+573001234567", "COP", "America/Bogota", true, false);
    }

    /** Registrado con Google, sin contrasena (FIN-52). */
    public static UserProfile unPerfilDeGoogle() {
        return new UserProfile(ID, EMAIL, "Ana", null, "+573001234567", "COP", "America/Bogota", false, true);
    }

    /** Las de unPerfil(): con contrasena y sin Google. */
    public static UserCredentials credenciales() {
        return new UserCredentials(ID, HASH, 0, null);
    }

    /** Las de unPerfilDeGoogle(): sin contrasena y con la cuenta de GoogleMother (FIN-53). */
    public static UserCredentials credencialesDeGoogle() {
        return new UserCredentials(ID, null, 0, GoogleMother.SUB);
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
