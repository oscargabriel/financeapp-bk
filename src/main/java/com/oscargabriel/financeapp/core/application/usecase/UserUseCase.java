package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.ChangePasswordCommand;
import com.oscargabriel.financeapp.core.domain.model.FirstPasswordCommand;
import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;
import com.oscargabriel.financeapp.core.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.core.domain.model.UsdRate;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;
import com.oscargabriel.financeapp.core.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.core.domain.port.in.UserPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class UserUseCase implements UserPort {

    /** La ventana de recencia del ID token de la primera contrasena, antes o despues de ahora (FIN-53, decision 2). */
    private static final Duration VENTANA = Duration.ofMinutes(5);

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort hasher;
    private final GoogleIdTokenVerifierPort google;
    private final CurrencyQueryPort monedas;
    private final ResolveExchangeRatePort tasas;
    private final Clock clock;

    @Override
    public Mono<UserProfile> get(UUID userId) {
        return usuarios.findActiveProfile(userId)
                .switchIfEmpty(Mono.error(UserUseCase::usuarioNoActivo));
    }

    /**
     * El formato ya viene validado por UpdateUserProfileRequest; aqui queda lo que necesita la base, en el
     * orden del design.md de FIN-29: la moneda, el correo y al final la tasa, la unica que puede salir al
     * proveedor.
     */
    @Override
    public Mono<UserProfile> update(UUID userId, UpdateUserProfileCommand command) {
        return Mono.defer(() -> usuarios.findActiveProfile(userId))
                .switchIfEmpty(Mono.error(UserUseCase::usuarioNoActivo))
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

    /** Las reglas de la nueva ya vienen validadas por ChangePasswordRequest. */
    @Override
    public Mono<Void> changePassword(UUID userId, ChangePasswordCommand command) {
        return Mono.defer(() -> usuarios.findActiveCredentials(userId))
                .switchIfEmpty(Mono.error(UserUseCase::usuarioNoActivo))
                .flatMap(UserUseCase::hashOSinContrasena)
                .filter(hash -> hasher.matches(command.currentPassword(), hash))
                .switchIfEmpty(Mono.error(UserUseCase::claveActualEquivocada))
                .flatMap(hash -> usuarios.updatePassword(userId, hasher.hash(command.newPassword())));
    }

    /**
     * La primera contrasena de un usuario registrado con Google (FIN-53). Exige confirmar la identidad en
     * ese momento: un ID token de la cuenta Google vinculada, emitido a 5 minutos o menos de ahora, antes
     * o despues (design.md de FIN-53, decision 2). Las reglas de la contrasena ya vienen validadas por
     * FirstPasswordRequest. El estado va antes que Google: no se verifica un token para algo que se va a
     * rechazar (decision 5).
     */
    @Override
    public Mono<Void> setFirstPassword(UUID userId, FirstPasswordCommand command) {
        return Mono.defer(() -> usuarios.findActiveCredentials(userId))
                .switchIfEmpty(Mono.error(UserUseCase::usuarioNoActivo))
                .filter(credenciales -> credenciales.passwordHash() == null)
                .switchIfEmpty(Mono.error(UserUseCase::yaTieneContrasena))
                .flatMap(credenciales -> confirmarIdentidad(credenciales, command.idToken()))
                .flatMap(credenciales -> usuarios.setFirstPassword(userId, credenciales.googleSub(),
                        hasher.hash(command.newPassword())))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(UserUseCase::yaTieneContrasena))
                .then();
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
                command.timezone() == null ? actual.timezone() : command.timezone().trim(),
                actual.hasPassword(),
                actual.googleLinked(),
                actual.telegramLinked());
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
                .onErrorMap(UserUseCase::sinTasa)
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
     * estan registrados sin conocer siquiera su propia contrasena. Y que tenga contrasena va antes que
     * pedirla: a quien no tiene ninguna no se le pide (design.md de FIN-53, decision 5).
     */
    private Mono<Void> autorizarCorreo(UUID userId, String email, String claveActual) {
        return usuarios.findActiveCredentials(userId)
                .flatMap(UserUseCase::hashOSinContrasena)
                .flatMap(hash -> claveActual == null || claveActual.isBlank()
                        ? Mono.error(() -> new BadRequestException(HttpStatus.BAD_REQUEST,
                                ErrorCodes.VALIDATION_ERROR, "Para cambiar el correo hay que enviar la contrasena actual",
                                "currentPassword"))
                        : Mono.just(hash))
                .filter(hash -> hasher.matches(claveActual, hash))
                .switchIfEmpty(Mono.error(UserUseCase::claveActualEquivocada))
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

    /** Invalido, fuera de la ventana o de otra cuenta Google: el mismo 400, sin decir cual. */
    private Mono<UserCredentials> confirmarIdentidad(UserCredentials credenciales, String idToken) {
        return google.verificar(idToken)
                .filter(this::reciente)
                .filter(identidad -> identidad.sub().equals(credenciales.googleSub()))
                .switchIfEmpty(Mono.error(UserUseCase::tokenDeGoogleRechazado))
                .thenReturn(credenciales);
    }

    private boolean reciente(GoogleIdentity identidad) {
        Instant emitidoEn = identidad.emitidoEn();
        Instant ahora = clock.instant();
        return emitidoEn != null
                && !emitidoEn.isBefore(ahora.minus(VENTANA))
                && !emitidoEn.isAfter(ahora.plus(VENTANA));
    }

    /**
     * El usuario del token ya no esta activo. El mismo 401 y el mismo texto que UnauthenticatedEntryPoint
     * y UsuarioDelToken: para el cliente es una sesion que ya no vale.
     */
    private static BadRequestException usuarioNoActivo() {
        return new BadRequestException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED,
                "Autenticacion requerida", "authorization");
    }

    /** 400 y no 401: el token es valido, y un 401 haria que el front cerrara la sesion. */
    private static BadRequestException claveActualEquivocada() {
        return new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_CREDENTIALS,
                "La contrasena actual no es correcta", "currentPassword");
    }

    /**
     * Un usuario registrado con Google no tiene contrasena que probar: la define primero con
     * POST /users/me/password (design.md de FIN-53, decision 4).
     */
    private static BadRequestException sinContrasena() {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.INVALID_STATE,
                "El usuario no tiene contrasena: primero debe definirla", "currentPassword");
    }

    /**
     * El hash para comparar la contrasena actual, o sinContrasena() si no tiene: el cambio de contrasena
     * y el del correo responden igual a ese usuario (FIN-53).
     */
    private static Mono<String> hashOSinContrasena(UserCredentials credenciales) {
        return credenciales.passwordHash() == null
                ? Mono.error(sinContrasena())
                : Mono.just(credenciales.passwordHash());
    }

    /** La primera contrasena se define una vez; despues se cambia con la actual (FIN-53). */
    private static BadRequestException yaTieneContrasena() {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.INVALID_STATE,
                "El usuario ya tiene contrasena: cambiala con la actual", "newPassword");
    }

    /**
     * Invalido, fuera de la ventana de recencia o de otra cuenta Google: no dice cual. 400 y no 401,
     * por lo mismo que claveActualEquivocada (design.md de FIN-53, decision 3).
     */
    private static BadRequestException tokenDeGoogleRechazado() {
        return new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_CREDENTIALS,
                "El token de Google no es valido", "idToken");
    }
}
