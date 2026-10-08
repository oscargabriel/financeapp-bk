package com.oscargabriel.financeapp.application.usecase;

import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;

/** Los errores que comparten la lectura, el parche y el cambio de contrasena del perfil. */
final class ErroresDelPerfil {

    private ErroresDelPerfil() {
    }

    /**
     * El usuario del token ya no esta activo. El mismo 401 y el mismo texto que UnauthenticatedEntryPoint
     * y UsuarioDelToken: para el cliente es una sesion que ya no vale.
     */
    static BadRequestException usuarioNoActivo() {
        return new BadRequestException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED,
                "Autenticacion requerida", "authorization");
    }

    /** 400 y no 401: el token es valido, y un 401 haria que el front cerrara la sesion. */
    static BadRequestException claveActualEquivocada() {
        return new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_CREDENTIALS,
                "La contrasena actual no es correcta", "currentPassword");
    }
}
