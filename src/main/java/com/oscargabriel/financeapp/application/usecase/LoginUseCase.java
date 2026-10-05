package com.oscargabriel.financeapp.application.usecase;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.AccessToken;
import com.oscargabriel.financeapp.domain.model.LoginCommand;
import com.oscargabriel.financeapp.domain.port.in.LoginPort;
import com.oscargabriel.financeapp.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.domain.port.out.TokenIssuerPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class LoginUseCase implements LoginPort {

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort hasher;
    private final TokenIssuerPort emisor;

    /**
     * Correo desconocido, usuario inactivo, usuario borrado y contrasena incorrecta terminan en el
     * mismo 401 con el mismo texto. Distinguirlos convertiria el login en un oraculo para saber que
     * correos estan registrados (OWASP A07). Que los dos campos vengan lo exige LoginRequest.
     */
    @Override
    public Mono<AccessToken> login(LoginCommand command) {
        return Mono.defer(() -> usuarios.findActiveByEmail(normalizarEmail(command.email()))
                .filter(credenciales -> hasher.matches(command.password(), credenciales.passwordHash()))
                .map(credenciales -> emisor.issueFor(credenciales.id()))
                .switchIfEmpty(Mono.error(LoginUseCase::credencialesInvalidas)));
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
