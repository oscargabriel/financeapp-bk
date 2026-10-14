package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;
import com.oscargabriel.financeapp.core.domain.model.GoogleRegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.model.User;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.GoogleRegisterPort;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class GoogleRegisterUseCase implements GoogleRegisterPort {

    /** La version de sesion con la que nace todo usuario: el DEFAULT de users.token_version. */
    private static final long VERSION_INICIAL = 0L;

    private final GoogleIdTokenVerifierPort verificador;
    private final UserRepositoryPort usuarios;
    private final ReglasDelAlta reglas;
    private final AperturaDeSesion aperturaDeSesion;
    private final Clock clock;

    /**
     * El orden del alta normal (design.md de FIN-52, decision 5): token, lista de admitidos, correo
     * libre y cuenta Google libre. El registro no vincula: un correo con cuenta recibe el 409 y el
     * front ofrece el login, que es el que vincula.
     */
    @Override
    public Mono<SessionTokens> register(GoogleRegistrationCommand command) {
        return Mono.defer(() -> verificador.verificar(command.idToken()))
                .switchIfEmpty(Mono.error(ErroresDeGoogle::tokenInvalido))
                .flatMap(identidad -> reglas.emailAdmitido(identidad.email())
                        .then(Mono.defer(() -> reglas.emailDisponible(identidad.email())))
                        .then(Mono.defer(() -> googleDisponible(identidad.sub())))
                        .then(Mono.fromSupplier(() -> nuevoUsuario(identidad, command.timezoneOrDefault()))))
                .flatMap(usuario -> usuarios.createWithDefaultCategories(usuario)
                        .then(Mono.defer(() -> aperturaDeSesion.abrir(usuario.id(), VERSION_INICIAL))));
    }

    /** Pasa si el usuario cambio su correo despues de registrarse con este mismo Google. */
    private Mono<Void> googleDisponible(String sub) {
        return usuarios.existsByGoogleSub(sub)
                .filter(existe -> !existe)
                .switchIfEmpty(Mono.error(ReglasDelAlta::yaRegistrado))
                .then();
    }

    /** Sin contrasena y con la moneda por defecto del alta: los dos se cambian luego desde el perfil. */
    private User nuevoUsuario(GoogleIdentity identidad, String zona) {
        return new User(
                UuidV7.from(clock.instant()),
                identidad.email(),
                null,
                identidad.firstName(),
                identidad.lastName(),
                RegistrationCommand.MONEDA_POR_DEFECTO,
                zona,
                null,
                identidad.sub());
    }
}
