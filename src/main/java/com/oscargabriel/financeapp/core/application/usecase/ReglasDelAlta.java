package com.oscargabriel.financeapp.core.application.usecase;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.port.out.RegistrationAllowlistPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

/**
 * Lo que comparten el alta con contrasena y el registro con Google (FIN-52): los dos pasan por la
 * lista de admitidos y los dos rechazan un correo ya registrado, con los mismos errores.
 */
@Component
public class ReglasDelAlta {

    private final UserRepositoryPort usuarios;
    private final RegistrationAllowlistPort admitidos;
    private final boolean restringido;

    public ReglasDelAlta(
            UserRepositoryPort usuarios,
            RegistrationAllowlistPort admitidos,
            @Value("${registro.admitidos.enabled}") boolean restringido) {
        this.usuarios = usuarios;
        this.admitidos = admitidos;
        this.restringido = restringido;
    }

    /** Va antes de emailDisponible: a un correo no admitido no se le dice si ya tiene cuenta. */
    public Mono<Void> emailAdmitido(String email) {
        if (!restringido) {
            return Mono.empty();
        }
        return admitidos.isAllowed(email)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.FORBIDDEN,
                        ErrorCodes.REGISTRATION_NOT_ALLOWED,
                        "El correo no esta admitido para registrarse", "email")))
                .then();
    }

    public Mono<Void> emailDisponible(String email) {
        return usuarios.existsByEmail(email)
                .filter(existe -> !existe)
                .switchIfEmpty(Mono.error(ReglasDelAlta::yaRegistrado))
                .then();
    }

    static BadRequestException yaRegistrado() {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "Ya hay una cuenta registrada con ese correo", "email");
    }
}
