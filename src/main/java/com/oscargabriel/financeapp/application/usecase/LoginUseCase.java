package com.oscargabriel.financeapp.application.usecase;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.AccessToken;
import com.oscargabriel.financeapp.domain.model.LoginCommand;
import com.oscargabriel.financeapp.domain.port.in.LoginPort;
import com.oscargabriel.financeapp.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.domain.port.out.TokenIssuerPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
public class LoginUseCase implements LoginPort {

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort hasher;
    private final TokenIssuerPort emisor;

    public LoginUseCase(UserRepositoryPort usuarios, PasswordHasherPort hasher,
            TokenIssuerPort emisor) {
        this.usuarios = usuarios;
        this.hasher = hasher;
        this.emisor = emisor;
    }

    /**
     * Correo desconocido, usuario inactivo, usuario borrado y contrasena incorrecta terminan en el
     * mismo 401 con el mismo texto. Distinguirlos convertiria el login en un oraculo para saber que
     * correos estan registrados (OWASP A07).
     */
    @Override
    public Mono<AccessToken> login(LoginCommand command) {
        return Mono.defer(() -> {
            validarFormato(command);

            return usuarios.findActiveByEmail(normalizarEmail(command.email()))
                    .filter(credenciales ->
                            hasher.matches(command.password(), credenciales.passwordHash()))
                    .map(credenciales -> emisor.issueFor(credenciales.id()))
                    .switchIfEmpty(Mono.error(LoginUseCase::credencialesInvalidas));
        });
    }

    /**
     * Un campo ausente es un error de forma, no un intento fallido: reportarlo como 400 no revela
     * nada sobre el usuario, y un 401 aqui dejaria al cliente adivinando por que no entra.
     */
    private static void validarFormato(LoginCommand command) {
        List<ErrorDetail> errores = new ArrayList<>();

        if (esVacio(command.email())) {
            errores.add(detalle("El correo es obligatorio", "email"));
        }
        if (esVacio(command.password())) {
            errores.add(detalle("La contrasena es obligatoria", "password"));
        }

        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
        }
    }

    /** El unico de finance.users es sobre lower(email), y asi lo guarda el alta. */
    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static ErrorDetail detalle(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(), descripcion, campo);
    }

    private static BadRequestException credencialesInvalidas() {
        return new BadRequestException(HttpStatus.UNAUTHORIZED, ErrorCodes.INVALID_CREDENTIALS,
                "Correo o contrasena incorrectos", "credentials");
    }
}
