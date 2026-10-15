package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.FirstPasswordCommand;
import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.port.in.SetFirstPasswordPort;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

/**
 * La primera contrasena de un usuario registrado con Google (FIN-53). Exige confirmar la identidad en
 * ese momento: un ID token de la cuenta Google vinculada, emitido a 5 minutos o menos de ahora, antes
 * o despues (design.md de FIN-53, decision 2). Las reglas de la contrasena ya vienen validadas por
 * FirstPasswordRequest.
 */
@Service
@AllArgsConstructor
public class SetFirstPasswordUseCase implements SetFirstPasswordPort {

    private static final Duration VENTANA = Duration.ofMinutes(5);

    private final UserRepositoryPort usuarios;
    private final GoogleIdTokenVerifierPort google;
    private final PasswordHasherPort hasher;
    private final Clock clock;

    /** El estado va antes que Google: no se verifica un token para algo que se va a rechazar (decision 5). */
    @Override
    public Mono<Void> define(UUID userId, FirstPasswordCommand command) {
        return Mono.defer(() -> usuarios.findActiveCredentials(userId))
                .switchIfEmpty(Mono.error(ErroresDelPerfil::usuarioNoActivo))
                .filter(credenciales -> credenciales.passwordHash() == null)
                .switchIfEmpty(Mono.error(ErroresDelPerfil::yaTieneContrasena))
                .flatMap(credenciales -> confirmarIdentidad(credenciales, command.idToken()))
                .flatMap(credenciales -> usuarios.setFirstPassword(userId, credenciales.googleSub(),
                        hasher.hash(command.newPassword())))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(ErroresDelPerfil::yaTieneContrasena))
                .then();
    }

    /** Invalido, fuera de la ventana o de otra cuenta Google: el mismo 400, sin decir cual. */
    private Mono<UserCredentials> confirmarIdentidad(UserCredentials credenciales, String idToken) {
        return google.verificar(idToken)
                .filter(this::reciente)
                .filter(identidad -> identidad.sub().equals(credenciales.googleSub()))
                .switchIfEmpty(Mono.error(ErroresDelPerfil::tokenDeGoogleRechazado))
                .thenReturn(credenciales);
    }

    private boolean reciente(GoogleIdentity identidad) {
        Instant emitidoEn = identidad.emitidoEn();
        Instant ahora = clock.instant();
        return emitidoEn != null
                && !emitidoEn.isBefore(ahora.minus(VENTANA))
                && !emitidoEn.isAfter(ahora.plus(VENTANA));
    }
}
