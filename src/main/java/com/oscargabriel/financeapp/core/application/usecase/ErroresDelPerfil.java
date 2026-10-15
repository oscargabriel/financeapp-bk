package com.oscargabriel.financeapp.core.application.usecase;

import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;

import reactor.core.publisher.Mono;

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

    /**
     * Un usuario registrado con Google no tiene contrasena que probar: la define primero con
     * POST /users/me/password (design.md de FIN-53, decision 4).
     */
    static BadRequestException sinContrasena() {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.INVALID_STATE,
                "El usuario no tiene contrasena: primero debe definirla", "currentPassword");
    }

    /**
     * El hash para comparar la contrasena actual, o sinContrasena() si no tiene: el cambio de contrasena
     * y el del correo responden igual a ese usuario (FIN-53).
     */
    static Mono<String> hashOSinContrasena(UserCredentials credenciales) {
        return credenciales.passwordHash() == null
                ? Mono.error(sinContrasena())
                : Mono.just(credenciales.passwordHash());
    }

    /** La primera contrasena se define una vez; despues se cambia con la actual (FIN-53). */
    static BadRequestException yaTieneContrasena() {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.INVALID_STATE,
                "El usuario ya tiene contrasena: cambiala con la actual", "newPassword");
    }

    /**
     * Invalido, fuera de la ventana de recencia o de otra cuenta Google: no dice cual. 400 y no 401,
     * por lo mismo que claveActualEquivocada (design.md de FIN-53, decision 3).
     */
    static BadRequestException tokenDeGoogleRechazado() {
        return new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_CREDENTIALS,
                "El token de Google no es valido", "idToken");
    }
}
