package com.oscargabriel.financeapp.core.application.usecase;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.port.in.GoogleLoginPort;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class GoogleLoginUseCase implements GoogleLoginPort {

    private final GoogleIdTokenVerifierPort verificador;
    private final UserRepositoryPort usuarios;
    private final AperturaDeSesion aperturaDeSesion;

    /**
     * Primero por la cuenta Google vinculada; si no hay, por el correo, para vincular la cuenta que
     * todavia no tiene Google (design.md de FIN-52, decision 5). Como el login con contrasena, no
     * consulta la lista de admitidos.
     */
    @Override
    public Mono<SessionTokens> login(String idToken) {
        return Mono.defer(() -> verificador.verificar(idToken))
                .switchIfEmpty(Mono.error(ErroresDeGoogle::tokenInvalido))
                .flatMap(identidad -> usuarios.findActiveByGoogleSub(identidad.sub())
                        .switchIfEmpty(Mono.defer(() -> vincularPorCorreo(identidad))))
                .flatMap(credenciales -> aperturaDeSesion.abrir(credenciales.id(), credenciales.tokenVersion()));
    }

    /**
     * Decir «no tienes cuenta» no reabre el oraculo de correos del login (OWASP A07): para recibirlo
     * hay que presentar un ID token valido de esa cuenta Google, y quien lo tiene ya es el dueno.
     */
    private Mono<UserCredentials> vincularPorCorreo(GoogleIdentity identidad) {
        return usuarios.findActiveByEmail(identidad.email())
                .switchIfEmpty(Mono.error(GoogleLoginUseCase::sinCuenta))
                .flatMap(credenciales -> credenciales.googleSub() != null
                        ? Mono.error(vinculadoAOtraCuenta())
                        : usuarios.linkGoogle(credenciales.id(), identidad.sub())
                                .filter(Boolean::booleanValue)
                                .switchIfEmpty(Mono.error(GoogleLoginUseCase::vinculadoAOtraCuenta))
                                .thenReturn(credenciales));
    }

    private static BadRequestException sinCuenta() {
        return new BadRequestException(HttpStatus.NOT_FOUND, ErrorCodes.ACCOUNT_NOT_REGISTERED,
                "No hay una cuenta registrada con esta cuenta de Google", "idToken");
    }

    private static BadRequestException vinculadoAOtraCuenta() {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "El correo ya esta vinculado a otra cuenta de Google", "email");
    }
}
