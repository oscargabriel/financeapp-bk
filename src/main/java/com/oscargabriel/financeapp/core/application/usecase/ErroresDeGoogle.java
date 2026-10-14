package com.oscargabriel.financeapp.core.application.usecase;

import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;

/** Los errores que comparten el registro y el login con Google (design.md de FIN-52, decision 4). */
final class ErroresDeGoogle {

    private ErroresDeGoogle() {
    }

    /** Una credencial del usuario que no sirve, como la contrasena equivocada. No dice por que. */
    static BadRequestException tokenInvalido() {
        return new BadRequestException(HttpStatus.UNAUTHORIZED, ErrorCodes.INVALID_CREDENTIALS,
                "El token de Google no es valido", "idToken");
    }
}
