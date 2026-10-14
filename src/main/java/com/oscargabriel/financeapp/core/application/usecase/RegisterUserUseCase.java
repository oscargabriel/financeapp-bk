package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.RegisteredUser;
import com.oscargabriel.financeapp.core.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.User;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.RegisterUserPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class RegisterUserUseCase implements RegisterUserPort {

    private final UserRepositoryPort usuarios;
    private final CurrencyQueryPort monedas;
    private final PasswordHasherPort hasher;
    private final ReglasDelAlta reglas;
    private final Clock clock;

    /** El formato ya viene validado por RegisterUserRequest; aqui queda lo que necesita la base. */
    @Override
    public Mono<RegisteredUser> register(RegistrationCommand command) {
        return Mono.defer(() -> {
            String email = normalizarEmail(command.email());
            String moneda = command.baseCurrencyCodeOrDefault();

            return monedaExiste(moneda)
                    .then(Mono.defer(() -> reglas.emailAdmitido(email)))
                    .then(Mono.defer(() -> reglas.emailDisponible(email)))
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
                opcional(command.phone()),
                null);
    }

    /** Un opcional en blanco se guarda como null, no como texto vacio. */
    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private Mono<Void> monedaExiste(String moneda) {
        return monedas.exists(moneda)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.BAD_REQUEST,
                        ErrorCodes.VALIDATION_ERROR,
                        "La moneda no existe en el catalogo", "baseCurrencyCode")))
                .then();
    }

    /** El unico de finance.users es sobre lower(email): guardarlo normalizado evita duplicados que solo difieren en mayusculas. */
    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }
}
