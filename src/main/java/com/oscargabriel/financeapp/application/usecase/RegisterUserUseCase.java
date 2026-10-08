package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.RegisteredUser;
import com.oscargabriel.financeapp.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.domain.model.User;
import com.oscargabriel.financeapp.domain.model.UuidV7;
import com.oscargabriel.financeapp.domain.port.in.RegisterUserPort;
import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class RegisterUserUseCase implements RegisterUserPort {

    private final UserRepositoryPort usuarios;
    private final CurrencyQueryPort monedas;
    private final PasswordHasherPort hasher;
    private final Clock clock;

    /** El formato ya viene validado por RegisterUserRequest; aqui queda lo que necesita la base. */
    @Override
    public Mono<RegisteredUser> register(RegistrationCommand command) {
        return Mono.defer(() -> {
            String email = normalizarEmail(command.email());
            String moneda = command.baseCurrencyCodeOrDefault();

            return monedaExiste(moneda)
                    .then(emailDisponible(email))
                    .then(Mono.fromSupplier(() -> nuevoUsuario(command, email, moneda)))
                    .flatMap(usuario -> usuarios.createWithDefaultCategories(usuario)
                            .map(copiadas -> new RegisteredUser(usuario, copiadas)));
        });
    }

    private User nuevoUsuario(RegistrationCommand command, String email, String moneda) {
        return new User(
                UuidV7.from(clock.instant()),
                email,
                hasher.hash(command.password()),
                command.firstName().trim(),
                opcional(command.lastName()),
                moneda,
                command.timezoneOrDefault(),
                opcional(command.phone()));
    }

    /** Un opcional en blanco se guarda como null, no como texto vacio. */
    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private Mono<Void> monedaExiste(String moneda) {
        return monedas.exists(moneda)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> unError(HttpStatus.BAD_REQUEST,
                        ErrorCodes.VALIDATION_ERROR,
                        "La moneda no existe en el catalogo", "baseCurrencyCode")))
                .then();
    }

    private Mono<Void> emailDisponible(String email) {
        return usuarios.existsByEmail(email)
                .filter(existe -> !existe)
                .switchIfEmpty(Mono.error(() -> unError(HttpStatus.CONFLICT,
                        ErrorCodes.DUPLICATE_RESOURCE,
                        "Ya hay una cuenta registrada con ese correo", "email")))
                .then();
    }

    /** El unico de finance.users es sobre lower(email): guardarlo normalizado evita duplicados que solo difieren en mayusculas. */
    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static BadRequestException unError(HttpStatus status, ErrorCodes codigo,
            String descripcion, String campo) {
        return new BadRequestException(status, codigo, descripcion, campo);
    }
}
