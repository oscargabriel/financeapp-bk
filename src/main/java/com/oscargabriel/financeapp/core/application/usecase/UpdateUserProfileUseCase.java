package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.core.domain.model.UsdRate;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;
import com.oscargabriel.financeapp.core.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.core.domain.port.in.UpdateUserProfilePort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class UpdateUserProfileUseCase implements UpdateUserProfilePort {

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort hasher;
    private final CurrencyQueryPort monedas;
    private final ResolveExchangeRatePort tasas;
    private final Clock clock;

    /**
     * El formato ya viene validado por UpdateUserProfileRequest; aqui queda lo que necesita la base, en el
     * orden del design.md de FIN-29: la moneda, el correo y al final la tasa, la unica que puede salir al
     * proveedor.
     */
    @Override
    public Mono<UserProfile> update(UUID userId, UpdateUserProfileCommand command) {
        return Mono.defer(() -> usuarios.findActiveProfile(userId))
                .switchIfEmpty(Mono.error(ErroresDelPerfil::usuarioNoActivo))
                .flatMap(actual -> {
                    UserProfile nuevo = aplicar(actual, command);
                    boolean otraMoneda = !nuevo.baseCurrencyCode().equals(actual.baseCurrencyCode());
                    Mono<Void> moneda = otraMoneda ? monedaActiva(nuevo.baseCurrencyCode()) : Mono.empty();
                    Mono<Void> permiso = nuevo.email().equals(actual.email())
                            ? Mono.empty()
                            : Mono.defer(() -> autorizarCorreo(userId, nuevo.email(), command.currentPassword()));
                    Mono<Void> tasa = otraMoneda ? Mono.defer(() -> tasaDeHoy(nuevo.baseCurrencyCode())) : Mono.empty();
                    return moneda.then(permiso).then(tasa).then(Mono.defer(() -> usuarios.updateProfile(nuevo)));
                });
    }

    private static UserProfile aplicar(UserProfile actual, UpdateUserProfileCommand command) {
        return new UserProfile(
                actual.id(),
                command.email() == null ? actual.email() : normalizarEmail(command.email()),
                command.firstName() == null ? actual.firstName() : command.firstName().trim(),
                command.lastName() == null ? actual.lastName() : opcional(command.lastName()),
                command.phone() == null ? actual.phone() : opcional(command.phone()),
                command.baseCurrencyCode() == null
                        ? actual.baseCurrencyCode()
                        : command.baseCurrencyCode().trim().toUpperCase(),
                command.timezone() == null ? actual.timezone() : command.timezone().trim());
    }

    private Mono<Void> monedaActiva(String moneda) {
        return monedas.exists(moneda)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.BAD_REQUEST,
                        ErrorCodes.VALIDATION_ERROR, "La moneda no es una moneda activa del catalogo",
                        "baseCurrencyCode")))
                .then();
    }

    /**
     * Sin ninguna tasa USD→moneda los totales de la persona saldrian en null: finance.in_currency no tiene con
     * que convertir. resolve ya pide la de hoy al proveedor si falta, con su limite de consultas; solo importa
     * que quede alguna fila, porque cada movimiento usa la de su fecha.
     */
    private Mono<Void> tasaDeHoy(String moneda) {
        return tasas.resolve(UsdRate.USD, moneda, LocalDate.now(clock))
                .onErrorMap(UpdateUserProfileUseCase::sinTasa)
                .then();
    }

    /** El mismo 502 que el alta de movimientos sin tasa (ConversorDeMontos): el proveedor no respondio. */
    private static Throwable sinTasa(Throwable error) {
        return error instanceof BadRequestException e && e.getHttpStatus() == HttpStatus.NOT_FOUND
                ? new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR,
                        "No hay tasa de cambio para la moneda nueva; intenta de nuevo", "server", error)
                : error;
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
