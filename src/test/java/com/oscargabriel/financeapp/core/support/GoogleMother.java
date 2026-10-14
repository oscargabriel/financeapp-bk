package com.oscargabriel.financeapp.core.support;

import java.time.Duration;

import com.oscargabriel.financeapp.core.domain.model.AccessToken;
import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;
import com.oscargabriel.financeapp.core.domain.model.RefreshToken;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;

/** Identidades de Google ya verificadas y la sesion que abren, para los casos de uso de FIN-52. */
public final class GoogleMother {

    public static final String ID_TOKEN = "un.id-token.de-google";
    public static final String SUB = "sub-123";

    public static final SessionTokens SESION = new SessionTokens(
            new AccessToken("un.jwt.firmado", Duration.ofHours(1)),
            new RefreshToken("un-refresh-opaco", Duration.ofHours(24)));

    private GoogleMother() {
    }

    /** El correo ya viene normalizado, como lo entrega el verificador. */
    public static GoogleIdentity unaIdentidad() {
        return new GoogleIdentity(SUB, UserMother.EMAIL, "Ana", "Gomez");
    }
}
