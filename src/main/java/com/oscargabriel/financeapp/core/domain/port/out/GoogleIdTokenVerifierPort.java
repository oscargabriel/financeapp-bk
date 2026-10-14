package com.oscargabriel.financeapp.core.domain.port.out;

import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;

import reactor.core.publisher.Mono;

public interface GoogleIdTokenVerifierPort {

    /**
     * La identidad de un ID token de Google valido para este cliente y con el correo verificado.
     * Vacio si el token no sirve, por la razon que sea. Error 502 EXTERNAL_SERVICE_ERROR si no se
     * pudo consultar a Google: eso no dice nada del token.
     */
    Mono<GoogleIdentity> verificar(String idToken);
}
