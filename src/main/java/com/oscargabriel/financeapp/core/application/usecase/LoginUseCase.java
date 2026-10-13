package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.LoginCommand;
import com.oscargabriel.financeapp.core.domain.model.NewRefreshToken;
import com.oscargabriel.financeapp.core.domain.model.RefreshToken;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.LoginPort;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.RefreshTokenGeneratorPort;
import com.oscargabriel.financeapp.core.domain.port.out.SesionRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TokenIssuerPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
public class LoginUseCase implements LoginPort {

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort hasher;
    private final TokenIssuerPort emisor;
    private final RefreshTokenGeneratorPort generador;
    private final SesionRepositoryPort sesiones;
    private final Clock clock;
    private final Duration duracionDeLaSesion;

    public LoginUseCase(
            UserRepositoryPort usuarios,
            PasswordHasherPort hasher,
            TokenIssuerPort emisor,
            RefreshTokenGeneratorPort generador,
            SesionRepositoryPort sesiones,
            Clock clock,
            @Value("${spring.security.jwt.refresh-expiration}") Duration duracionDeLaSesion) {
        this.usuarios = usuarios;
        this.hasher = hasher;
        this.emisor = emisor;
        this.generador = generador;
        this.sesiones = sesiones;
        this.clock = clock;
        this.duracionDeLaSesion = duracionDeLaSesion;
    }

    /**
     * Correo desconocido, usuario inactivo, usuario borrado y contrasena incorrecta terminan en el
     * mismo 401 con el mismo texto. Distinguirlos convertiria el login en un oraculo para saber que
     * correos estan registrados (OWASP A07). Que los dos campos vengan lo exige LoginRequest.
     */
    @Override
    public Mono<SessionTokens> login(LoginCommand command) {
        return Mono.defer(() -> usuarios.findActiveByEmail(normalizarEmail(command.email()))
                .filter(credenciales -> hasher.matches(command.password(), credenciales.passwordHash()))
                .switchIfEmpty(Mono.error(LoginUseCase::credencialesInvalidas))
                .flatMap(this::abrirSesion));
    }

    /**
     * Abre una familia nueva de refresh tokens que vence a las {@code duracionDeLaSesion} del login.
     * La base guarda el hash; el valor solo viaja en la respuesta.
     */
    private Mono<SessionTokens> abrirSesion(UserCredentials credenciales) {
        Instant ahora = clock.instant();
        String refresh = generador.generar();
        NewRefreshToken fila = new NewRefreshToken(
                UuidV7.from(ahora),
                credenciales.id(),
                UuidV7.from(ahora),
                generador.hash(refresh),
                credenciales.tokenVersion(),
                ahora.plus(duracionDeLaSesion),
                ahora);

        return sesiones.abrir(fila)
                .then(Mono.fromSupplier(() -> new SessionTokens(
                        emisor.issueFor(credenciales.id(), credenciales.tokenVersion()),
                        new RefreshToken(refresh, duracionDeLaSesion))));
    }

    /** El unico de finance.users es sobre lower(email), y asi lo guarda el alta. */
    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static BadRequestException credencialesInvalidas() {
        return new BadRequestException(HttpStatus.UNAUTHORIZED, ErrorCodes.INVALID_CREDENTIALS,
                "Correo o contrasena incorrectos", "credentials");
    }
}
