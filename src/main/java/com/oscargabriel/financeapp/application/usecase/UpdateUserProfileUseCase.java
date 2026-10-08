package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.domain.model.UserProfile;
import com.oscargabriel.financeapp.domain.port.in.UpdateUserProfilePort;
import com.oscargabriel.financeapp.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class UpdateUserProfileUseCase implements UpdateUserProfilePort {

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort hasher;

    /** El formato ya viene validado por UpdateUserProfileRequest; aqui queda lo que necesita la base. */
    @Override
    public Mono<UserProfile> update(UUID userId, UpdateUserProfileCommand command) {
        return Mono.defer(() -> usuarios.findActiveProfile(userId))
                .switchIfEmpty(Mono.error(ErroresDelPerfil::usuarioNoActivo))
                .flatMap(actual -> {
                    UserProfile nuevo = aplicar(actual, command);
                    Mono<Void> permiso = nuevo.email().equals(actual.email())
                            ? Mono.empty()
                            : autorizarCorreo(userId, nuevo.email(), command.currentPassword());
                    return permiso.then(Mono.defer(() -> usuarios.updateProfile(nuevo)));
                });
    }

    /** La moneda base se conserva siempre: su cambio es de FA-91. */
    private static UserProfile aplicar(UserProfile actual, UpdateUserProfileCommand command) {
        return new UserProfile(
                actual.id(),
                command.email() == null ? actual.email() : normalizarEmail(command.email()),
                command.firstName() == null ? actual.firstName() : command.firstName().trim(),
                command.lastName() == null ? actual.lastName() : opcional(command.lastName()),
                command.phone() == null ? actual.phone() : opcional(command.phone()),
                actual.baseCurrencyCode(),
                command.timezone() == null ? actual.timezone() : command.timezone().trim());
    }

    /**
     * La clave va antes que la unicidad: si no, cualquiera con un token podria preguntar que correos
     * estan registrados sin conocer siquiera su propia contrasena.
     */
    private Mono<Void> autorizarCorreo(UUID userId, String email, String claveActual) {
        if (claveActual == null || claveActual.isBlank()) {
            return Mono.error(() -> new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    "Para cambiar el correo hay que enviar la contrasena actual", "currentPassword"));
        }
        return usuarios.findActivePasswordHash(userId)
                .filter(hash -> hasher.matches(claveActual, hash))
                .switchIfEmpty(Mono.error(ErroresDelPerfil::claveActualEquivocada))
                .then(Mono.defer(() -> usuarios.existsByEmailForOtherUser(email, userId)))
                .filter(existe -> !existe)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.CONFLICT,
                        ErrorCodes.DUPLICATE_RESOURCE, "Ya hay una cuenta registrada con ese correo", "email")))
                .then();
    }

    /** El unico de finance.users es sobre lower(email), y asi lo guarda el alta. */
    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }

    /** En blanco borra el valor: con null como "no cambia", es la unica forma de vaciar un opcional. */
    private static String opcional(String valor) {
        return valor.isBlank() ? null : valor.trim();
    }
}
