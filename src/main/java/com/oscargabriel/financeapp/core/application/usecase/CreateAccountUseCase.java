package com.oscargabriel.financeapp.core.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.AccountType;
import com.oscargabriel.financeapp.core.domain.model.CreateAccountCommand;
import com.oscargabriel.financeapp.core.domain.model.NewAccount;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.CreateAccountPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class CreateAccountUseCase implements CreateAccountPort {

    private final AccountRepositoryPort cuentas;
    private final CurrencyQueryPort monedas;
    private final Clock clock;

    /**
     * El formato, incluidos los campos de credito segun el tipo, ya viene validado por
     * CreateAccountRequest; aqui queda que la moneda este activa, que necesita la base.
     */
    @Override
    public Mono<Account> create(UUID userId, CreateAccountCommand command) {
        return Mono.defer(() -> {
            String moneda = command.currencyCode().trim().toUpperCase();

            return monedaActiva(moneda)
                    .then(Mono.fromSupplier(() -> nuevaCuenta(userId, command, moneda)))
                    .flatMap(cuentas::create);
        });
    }

    /** Una tarjeta sin la opcion de interes a 1 cuota no lo cobra (FA-134); las demas cuentas no la tienen. */
    private NewAccount nuevaCuenta(UUID userId, CreateAccountCommand command, String moneda) {
        AccountType tipo = AccountType.valueOf(command.type().trim().toUpperCase());
        return new NewAccount(
                UuidV7.from(clock.instant()),
                userId,
                command.name().trim(),
                tipo,
                moneda,
                command.initialBalance() == null ? BigDecimal.ZERO : command.initialBalance(),
                command.creditLimit(),
                command.statementDay(),
                command.paymentDueDay(),
                command.monthlyInterestRate(),
                tipo == AccountType.CREDIT
                        ? Boolean.TRUE.equals(command.chargesSingleInstallmentInterest())
                        : null);
    }

    private Mono<Void> monedaActiva(String moneda) {
        return monedas.exists(moneda)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.BAD_REQUEST,
                        ErrorCodes.VALIDATION_ERROR, "La moneda no existe en el catalogo o no esta activa",
                        "currencyCode")))
                .then();
    }

}
