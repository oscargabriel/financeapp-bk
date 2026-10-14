package com.oscargabriel.financeapp.core.application.usecase;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.LoginCommand;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.port.in.LoginPort;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class LoginUseCase implements LoginPort {

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort hasher;
    private final AperturaDeSesion aperturaDeSesion;

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
                .switchIfEmpty(Mono.error(LoginUseCase::credencialesInvalidas))
                .flatMap(credenciales -> aperturaDeSesion.abrir(credenciales.id(), credenciales.tokenVersion())));
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
