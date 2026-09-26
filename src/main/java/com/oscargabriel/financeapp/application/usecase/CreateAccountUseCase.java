package com.oscargabriel.financeapp.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.CreateAccountCommand;
import com.oscargabriel.financeapp.domain.model.NewAccount;
import com.oscargabriel.financeapp.domain.model.UuidV7;
import com.oscargabriel.financeapp.domain.port.in.CreateAccountPort;
import com.oscargabriel.financeapp.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;

import reactor.core.publisher.Mono;

@Service
public class CreateAccountUseCase implements CreateAccountPort {

    private static final Pattern FORMATO_MONEDA = Pattern.compile("^[A-Za-z]{3}$");

    /** El largo de finance.accounts.name. */
    private static final int LARGO_MAXIMO_NOMBRE = 80;

    /** NUMERIC(18,4): 4 decimales y 14 digitos enteros. Pasarse daria un error de la base, no un 400. */
    private static final int DECIMALES_MAXIMOS = 4;
    private static final BigDecimal TOPE_MONTO = new BigDecimal("100000000000000");

    private final AccountRepositoryPort cuentas;
    private final CurrencyQueryPort monedas;
    private final Clock clock;

    public CreateAccountUseCase(AccountRepositoryPort cuentas, CurrencyQueryPort monedas, Clock clock) {
        this.cuentas = cuentas;
        this.monedas = monedas;
        this.clock = clock;
    }

    /**
     * El formato se valida entero antes de ir a la base: los campos de credito mal puestos tienen que
     * salir como 400 y no llegar a ck_accounts_credit_fields.
     */
    @Override
    public Mono<Account> create(UUID userId, CreateAccountCommand command) {
        return Mono.defer(() -> {
            validarFormato(command);

            String moneda = command.currencyCode().trim().toUpperCase();

            return monedaActiva(moneda)
                    .then(Mono.fromSupplier(() -> nuevaCuenta(userId, command, moneda)))
                    .flatMap(cuentas::create);
        });
    }

    private NewAccount nuevaCuenta(UUID userId, CreateAccountCommand command, String moneda) {
        return new NewAccount(
                UuidV7.from(clock.instant()),
                userId,
                command.name().trim(),
                tipo(command.type()),
                moneda,
                command.initialBalance() == null ? BigDecimal.ZERO : command.initialBalance(),
                command.creditLimit(),
                command.statementDay(),
                command.paymentDueDay());
    }

    private Mono<Void> monedaActiva(String moneda) {
        return monedas.exists(moneda)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.BAD_REQUEST,
                        ErrorCodes.VALIDATION_ERROR, "La moneda no existe en el catalogo o no esta activa",
                        "currencyCode")))
                .then();
    }

    /** Acumula todos los campos invalidos en una sola respuesta, como el registro de usuario. */
    private static void validarFormato(CreateAccountCommand command) {
        List<ErrorDetail> errores = new ArrayList<>();

        if (esVacio(command.name())) {
            errores.add(detalle("El nombre es obligatorio", "name"));
        } else if (command.name().trim().length() > LARGO_MAXIMO_NOMBRE) {
            errores.add(detalle("El nombre no puede superar los " + LARGO_MAXIMO_NOMBRE + " caracteres",
                    "name"));
        }

        AccountType tipo = null;
        if (esVacio(command.type())) {
            errores.add(detalle("El tipo es obligatorio", "type"));
        } else {
            tipo = tipo(command.type());
            if (tipo == null) {
                errores.add(detalle("El tipo debe ser uno de CASH, DEBIT, CREDIT, SAVINGS, INVESTMENT u OTHER",
                        "type"));
            }
        }

        if (esVacio(command.currencyCode())) {
            errores.add(detalle("La moneda es obligatoria", "currencyCode"));
        } else if (!FORMATO_MONEDA.matcher(command.currencyCode().trim()).matches()) {
            errores.add(detalle("La moneda debe ser un codigo de tres letras", "currencyCode"));
        }

        if (command.initialBalance() != null && !cabeEnLaColumna(command.initialBalance())) {
            errores.add(detalle(fueraDeRango("El saldo inicial"), "initialBalance"));
        }

        if (command.currentBalance() != null) {
            errores.add(detalle("El saldo vigente lo calcula el sistema; envia initialBalance",
                    "currentBalance"));
        }

        // Con el tipo invalido no se sabe si los campos de credito sobran: ya hay un error en type.
        if (tipo == AccountType.CREDIT) {
            validarCamposDeCredito(command, errores);
        } else if (tipo != null) {
            rechazarCamposDeCredito(command, errores);
        }

        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
        }
    }

    private static void validarCamposDeCredito(CreateAccountCommand command, List<ErrorDetail> errores) {
        BigDecimal limite = command.creditLimit();
        if (limite != null && (limite.signum() <= 0 || !cabeEnLaColumna(limite))) {
            errores.add(detalle(fueraDeRango("El cupo debe ser mayor que cero y"), "creditLimit"));
        }
        if (!esDiaDelMes(command.statementDay())) {
            errores.add(detalle("El dia de corte debe estar entre 1 y 31", "statementDay"));
        }
        if (!esDiaDelMes(command.paymentDueDay())) {
            errores.add(detalle("El dia de pago debe estar entre 1 y 31", "paymentDueDay"));
        }
    }

    private static void rechazarCamposDeCredito(CreateAccountCommand command, List<ErrorDetail> errores) {
        if (command.creditLimit() != null) {
            errores.add(detalle("Solo una cuenta CREDIT tiene cupo", "creditLimit"));
        }
        if (command.statementDay() != null) {
            errores.add(detalle("Solo una cuenta CREDIT tiene dia de corte", "statementDay"));
        }
        if (command.paymentDueDay() != null) {
            errores.add(detalle("Solo una cuenta CREDIT tiene dia de pago", "paymentDueDay"));
        }
    }

    private static AccountType tipo(String valor) {
        try {
            return AccountType.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean cabeEnLaColumna(BigDecimal monto) {
        return monto.stripTrailingZeros().scale() <= DECIMALES_MAXIMOS
                && monto.abs().compareTo(TOPE_MONTO) < 0;
    }

    private static String fueraDeRango(String prefijo) {
        return prefijo + " admite hasta " + DECIMALES_MAXIMOS + " decimales y menos de 14 digitos enteros";
    }

    private static boolean esDiaDelMes(Integer dia) {
        return dia == null || (dia >= 1 && dia <= 31);
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static ErrorDetail detalle(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(), descripcion, campo);
    }
}
