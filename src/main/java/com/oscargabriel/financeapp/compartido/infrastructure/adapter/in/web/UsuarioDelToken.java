package com.oscargabriel.financeapp.compartido.infrastructure.adapter.in.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;

/**
 * El usuario de cada peticion sale del token y no de la URL: mientras el cliente eligiera de quien
 * son los datos, cualquier token valido podia pedir los de cualquiera.
 */
public final class UsuarioDelToken {

    private UsuarioDelToken() {
    }

    /**
     * Un subject que no sea UUID solo puede venir de un token firmado con la clave de esta
     * aplicacion y emitido por otro: no identifica a nadie, asi que se rechaza con el mismo 401
     * que UnauthenticatedEntryPoint y no con un 400, que insinuaria un parametro corregible.
     */
    public static UUID de(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED,
                    "Autenticacion requerida", "authorization", e);
        }
    }
}
