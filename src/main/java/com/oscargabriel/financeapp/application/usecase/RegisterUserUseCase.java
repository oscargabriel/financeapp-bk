package com.oscargabriel.financeapp.application.usecase;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
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
public class RegisterUserUseCase implements RegisterUserPort {

    /** El mismo patron que el CHECK ck_users_email, para no aceptar aqui lo que la base rechaza. */
    private static final Pattern FORMATO_EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");

    private static final Pattern FORMATO_MONEDA = Pattern.compile("^[A-Za-z]{3}$");

    private static final int LARGO_MINIMO_CLAVE = 8;

    /** BCrypt ignora todo lo que pase de 72 bytes, asi que una clave mas larga no seria la que el usuario cree. */
    private static final int BYTES_MAXIMOS_CLAVE = 72;

    private final UserRepositoryPort usuarios;
    private final CurrencyQueryPort monedas;
    private final PasswordHasherPort hasher;
    private final Clock clock;

    public RegisterUserUseCase(UserRepositoryPort usuarios, CurrencyQueryPort monedas,
            PasswordHasherPort hasher, Clock clock) {
        this.usuarios = usuarios;
        this.monedas = monedas;
        this.hasher = hasher;
        this.clock = clock;
    }

    /**
     * El defer mantiene el contrato reactivo: un payload invalido sale como senal de error del Mono,
     * no como excepcion lanzada al ensamblar la cadena.
     */
    @Override
    public Mono<RegisteredUser> register(RegistrationCommand command) {
        return Mono.defer(() -> {
            validarFormato(command);

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
                command.lastName() == null || command.lastName().isBlank()
                        ? null
                        : command.lastName().trim(),
                moneda,
                command.timezoneOrDefault());
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

    /**
     * Acumula todos los campos invalidos en una sola respuesta: obligar al cliente a descubrirlos de
     * uno en uno, a base de reintentos, seria gratuito solo para nosotros.
     */
    private static void validarFormato(RegistrationCommand command) {
        List<ErrorDetail> errores = new ArrayList<>();

        if (esVacio(command.email())) {
            errores.add(detalle("El correo es obligatorio", "email"));
        } else if (command.email().trim().length() > 255
                || !FORMATO_EMAIL.matcher(command.email().trim()).matches()) {
            errores.add(detalle("El correo no tiene un formato valido", "email"));
        }

        if (esVacio(command.password())) {
            errores.add(detalle("La contrasena es obligatoria", "password"));
        } else if (command.password().length() < LARGO_MINIMO_CLAVE) {
            errores.add(detalle(
                    "La contrasena debe tener al menos " + LARGO_MINIMO_CLAVE + " caracteres",
                    "password"));
        } else if (command.password().getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS_CLAVE) {
            errores.add(detalle(
                    "La contrasena no puede superar los " + BYTES_MAXIMOS_CLAVE + " bytes",
                    "password"));
        }

        if (esVacio(command.firstName())) {
            errores.add(detalle("El nombre es obligatorio", "firstName"));
        } else if (command.firstName().trim().length() > 100) {
            errores.add(detalle("El nombre no puede superar los 100 caracteres", "firstName"));
        }

        if (command.lastName() != null && command.lastName().trim().length() > 100) {
            errores.add(detalle("El apellido no puede superar los 100 caracteres", "lastName"));
        }

        if (!FORMATO_MONEDA.matcher(command.baseCurrencyCodeOrDefault()).matches()) {
            errores.add(detalle("La moneda debe ser un codigo de tres letras", "baseCurrencyCode"));
        }

        if (!esZonaConocida(command.timezoneOrDefault())) {
            errores.add(detalle("La zona horaria no es una zona IANA conocida", "timezone"));
        }

        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
        }
    }

    private static boolean esZonaConocida(String zona) {
        try {
            ZoneId.of(zona);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /** El unico de finance.users es sobre lower(email): guardarlo normalizado evita duplicados que solo difieren en mayusculas. */
    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static ErrorDetail detalle(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(), descripcion, campo);
    }

    private static BadRequestException unError(HttpStatus status, ErrorCodes codigo,
            String descripcion, String campo) {
        return new BadRequestException(status, codigo, descripcion, campo);
    }
}
