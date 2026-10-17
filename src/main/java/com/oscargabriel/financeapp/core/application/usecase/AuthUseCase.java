package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;
import com.oscargabriel.financeapp.core.domain.model.GoogleRegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.LoginCommand;
import com.oscargabriel.financeapp.core.domain.model.RefreshToken;
import com.oscargabriel.financeapp.core.domain.model.RegisteredUser;
import com.oscargabriel.financeapp.core.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.model.User;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.AuthPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.RefreshTokenGeneratorPort;
import com.oscargabriel.financeapp.core.domain.port.out.SesionRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TokenIssuerPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class AuthUseCase implements AuthPort {

    /** La version de sesion con la que nace todo usuario: el DEFAULT de users.token_version. */
    private static final long VERSION_INICIAL = 0L;

    private final UserRepositoryPort usuarios;
    private final CurrencyQueryPort monedas;
    private final PasswordHasherPort hasher;
    private final GoogleIdTokenVerifierPort verificador;
    private final SesionRepositoryPort sesiones;
    private final RefreshTokenGeneratorPort generador;
    private final TokenIssuerPort emisor;
    private final ReglasDelAlta reglas;
    private final AperturaDeSesion aperturaDeSesion;
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

    /**
     * Correo desconocido, usuario inactivo, usuario borrado, usuario sin contrasena (FIN-52) y
     * contrasena incorrecta terminan en el mismo 401 con el mismo texto. Distinguirlos convertiria el
     * login en un oraculo para saber que correos estan registrados (OWASP A07). Que los dos campos
     * vengan lo exige LoginRequest.
     */
    @Override
    public Mono<SessionTokens> login(LoginCommand command) {
        return Mono.defer(() -> usuarios.findActiveByEmail(normalizarEmail(command.email()))
                .filter(credenciales -> credenciales.passwordHash() != null
                        && hasher.matches(command.password(), credenciales.passwordHash()))
                .switchIfEmpty(Mono.error(AuthUseCase::credencialesInvalidas))
                .flatMap(credenciales -> aperturaDeSesion.abrir(credenciales.id(), credenciales.tokenVersion())));
    }

    /**
     * El orden del alta normal (design.md de FIN-52, decision 5): token, lista de admitidos, correo
     * libre y cuenta Google libre. El registro no vincula: un correo con cuenta recibe el 409 y el
     * front ofrece el login, que es el que vincula.
     */
    @Override
    public Mono<SessionTokens> googleRegister(GoogleRegistrationCommand command) {
        return Mono.defer(() -> verificador.verificar(command.idToken()))
                .switchIfEmpty(Mono.error(AuthUseCase::tokenInvalido))
                .flatMap(identidad -> reglas.emailAdmitido(identidad.email())
                        .then(Mono.defer(() -> reglas.emailDisponible(identidad.email())))
                        .then(Mono.defer(() -> googleDisponible(identidad.sub())))
                        .then(Mono.fromSupplier(() -> nuevoUsuarioDeGoogle(identidad, command.timezoneOrDefault()))))
                .flatMap(usuario -> usuarios.createWithDefaultCategories(usuario)
                        .then(Mono.defer(() -> aperturaDeSesion.abrir(usuario.id(), VERSION_INICIAL))));
    }

    /**
     * Primero por la cuenta Google vinculada; si no hay, por el correo, para vincular la cuenta que
     * todavia no tiene Google (design.md de FIN-52, decision 5). Como el login con contrasena, no
     * consulta la lista de admitidos.
     */
    @Override
    public Mono<SessionTokens> googleLogin(String idToken) {
        return Mono.defer(() -> verificador.verificar(idToken))
                .switchIfEmpty(Mono.error(AuthUseCase::tokenInvalido))
                .flatMap(identidad -> usuarios.findActiveByGoogleSub(identidad.sub())
                        .switchIfEmpty(Mono.defer(() -> vincularPorCorreo(identidad))))
                .flatMap(credenciales -> aperturaDeSesion.abrir(credenciales.id(), credenciales.tokenVersion()));
    }

    /**
     * Cada refresh sirve una vez. Si no se pudo rotar, se revoca su sesion entera antes de responder:
     * cuando el motivo es un reuso, eso deja sin servir el reemplazo que tiene quien lo uso primero,
     * y el legitimo y el atacante vuelven los dos al login.
     */
    @Override
    public Mono<SessionTokens> refresh(String refreshToken) {
        return Mono.defer(() -> {
            Instant ahora = clock.instant();
            String hashUsado = generador.hash(refreshToken);
            String nuevo = generador.generar();

            return sesiones.rotar(hashUsado, UuidV7.from(ahora), generador.hash(nuevo), ahora)
                    .map(sesion -> new SessionTokens(
                            emisor.issueFor(sesion.userId(), sesion.tokenVersion()),
                            new RefreshToken(nuevo, Duration.between(ahora, sesion.expiresAt()))))
                    .switchIfEmpty(Mono.defer(() -> sesiones.revocarSesionDe(hashUsado, ahora))
                            .then(Mono.error(AuthUseCase::refreshInvalido)));
        });
    }

    /**
     * Revoca la sesion entera, no solo ese refresh: es la misma cosa, porque en una sesion solo hay
     * un refresh vivo. El access sigue sirviendo hasta vencer; el cliente lo descarta.
     */
    @Override
    public Mono<Void> logout(String refreshToken) {
        return Mono.defer(() -> sesiones.revocarSesionDe(generador.hash(refreshToken), clock.instant()));
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

    private static BadRequestException credencialesInvalidas() {
        return new BadRequestException(HttpStatus.UNAUTHORIZED, ErrorCodes.INVALID_CREDENTIALS,
                "Correo o contrasena incorrectos", "credentials");
    }

    /** Pasa si el usuario cambio su correo despues de registrarse con este mismo Google. */
    private Mono<Void> googleDisponible(String sub) {
        return usuarios.existsByGoogleSub(sub)
                .filter(existe -> !existe)
                .switchIfEmpty(Mono.error(ReglasDelAlta::yaRegistrado))
                .then();
    }

    /** Sin contrasena y con la moneda por defecto del alta: los dos se cambian luego desde el perfil. */
    private User nuevoUsuarioDeGoogle(GoogleIdentity identidad, String zona) {
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

    /**
     * Decir «no tienes cuenta» no reabre el oraculo de correos del login (OWASP A07): para recibirlo
     * hay que presentar un ID token valido de esa cuenta Google, y quien lo tiene ya es el dueno.
     */
    private Mono<UserCredentials> vincularPorCorreo(GoogleIdentity identidad) {
        return usuarios.findActiveByEmail(identidad.email())
                .switchIfEmpty(Mono.error(AuthUseCase::sinCuenta))
                .flatMap(credenciales -> credenciales.googleSub() != null
                        ? Mono.error(vinculadoAOtraCuenta())
                        : usuarios.linkGoogle(credenciales.id(), identidad.sub())
                                .filter(Boolean::booleanValue)
                                .switchIfEmpty(Mono.error(AuthUseCase::vinculadoAOtraCuenta))
                                .thenReturn(credenciales));
    }

    /**
     * Una credencial del usuario que no sirve, como la contrasena equivocada. No dice por que. El mismo
     * error para el registro y el login con Google (design.md de FIN-52, decision 4).
     */
    private static BadRequestException tokenInvalido() {
        return new BadRequestException(HttpStatus.UNAUTHORIZED, ErrorCodes.INVALID_CREDENTIALS,
                "El token de Google no es valido", "idToken");
    }

    private static BadRequestException sinCuenta() {
        return new BadRequestException(HttpStatus.NOT_FOUND, ErrorCodes.ACCOUNT_NOT_REGISTERED,
                "No hay una cuenta registrada con esta cuenta de Google", "idToken");
    }

    private static BadRequestException vinculadoAOtraCuenta() {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "El correo ya esta vinculado a otra cuenta de Google", "email");
    }

    /**
     * El mismo 401 y el mismo texto que el resto de la API, en el campo del refresh: el cliente solo
     * tiene que saber que vuelve al login, no cual de los motivos fue.
     */
    private static BadRequestException refreshInvalido() {
        return new BadRequestException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED,
                "Autenticacion requerida", "refreshToken");
    }
}
