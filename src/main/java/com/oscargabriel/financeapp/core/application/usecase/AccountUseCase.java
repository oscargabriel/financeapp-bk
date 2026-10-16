package com.oscargabriel.financeapp.core.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.AccountType;
import com.oscargabriel.financeapp.core.domain.model.CreateAccountCommand;
import com.oscargabriel.financeapp.core.domain.model.NewAccount;
import com.oscargabriel.financeapp.core.domain.model.UpdateAccountCommand;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.AccountPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.RecurrenceRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class AccountUseCase implements AccountPort {

    private final AccountQueryPort query;
    private final SeriesAlDia alDia;
    private final AccountRepositoryPort cuentas;
    private final CurrencyQueryPort monedas;
    private final RecurrenceRepositoryPort series;
    private final Clock clock;

    @Override
    public Flux<Account> list(UUID userId, boolean includeInactive) {
        return alDia.ponerAlDia(userId).thenMany(Flux.defer(() -> query.findByUser(userId, includeInactive)));
    }

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

    /**
     * El formato del parche ya viene validado por UpdateAccountRequest, incluidos los rangos y que
     * no traiga saldo vigente ni tipo. Aqui queda lo que depende de la cuenta guardada: su tipo, su
     * moneda y sus movimientos. Ningun chequeo escribe; solo el update final, que lleva tambien el
     * estado: un parche rechazado no desactiva ni reactiva nada.
     */
    @Override
    public Mono<Account> update(UUID userId, UUID accountId, UpdateAccountCommand parche) {
        return cuentas.findActiveByIdAndUser(accountId, userId)
                .switchIfEmpty(Mono.error(AccountUseCase::noEncontrada))
                .flatMap(guardada -> {
                    camposDeCreditoSegunElTipo(guardada.type(), parche);
                    Account resultante = aplicar(guardada, parche);
                    saldoSuficiente(guardada, resultante);
                    return monedaCambiable(guardada, resultante.currencyCode())
                            .then(Mono.defer(() -> sinSeriesSiSeDesactiva(userId, guardada, resultante)))
                            .then(Mono.defer(() -> cuentas.update(userId, resultante)));
                })
                .switchIfEmpty(Mono.error(AccountUseCase::noEncontrada));
    }

    /**
     * A diferencia de una categoria, el borrado depende de la fila: una cuenta con saldo sacaria ese
     * dinero, o esa deuda, del listado sin que el usuario lo decida. Por eso lee antes de marcar. Lo
     * programado tampoco cuenta en el saldo vigente y se quedaria en la cuenta borrada (FA-115).
     */
    @Override
    public Mono<Void> delete(UUID userId, UUID accountId) {
        return cuentas.findActiveByIdAndUser(accountId, userId)
                .switchIfEmpty(Mono.error(AccountUseCase::noEncontrada))
                .flatMap(cuenta -> Mono.zip(cuentas.countScheduled(accountId),
                                series.countActiveByAccount(accountId, userId))
                        .flatMap(enUso -> {
                            List<ErrorDetail> errores = impedimentos(cuenta, enUso.getT1(), enUso.getT2());
                            return errores.isEmpty()
                                    ? cuentas.softDelete(accountId, userId)
                                    : Mono.error(new BadRequestException(HttpStatus.CONFLICT, errores));
                        }))
                .flatMap(borrada -> borrada
                        ? Mono.<Void>empty()
                        : Mono.error(noEncontrada()));
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

    /**
     * El saldo vigente y el cupo comprometido se copian tal cual: si cambia el inicial, la base lo corre
     * en la misma diferencia (trg_accounts_shift_balance) y el RETURNING del update trae el resultado.
     */
    private static Account aplicar(Account guardada, UpdateAccountCommand parche) {
        return new Account(
                guardada.id(),
                parche.name() == null ? guardada.name() : parche.name().trim(),
                guardada.type(),
                parche.currencyCode() == null
                        ? guardada.currencyCode()
                        : parche.currencyCode().trim().toUpperCase(),
                parche.initialBalance() == null ? guardada.initialBalance() : parche.initialBalance(),
                guardada.currentBalance(),
                parche.creditLimit() == null ? guardada.creditLimit() : parche.creditLimit(),
                parche.statementDay() == null ? guardada.statementDay() : parche.statementDay(),
                parche.paymentDueDay() == null ? guardada.paymentDueDay() : parche.paymentDueDay(),
                parche.monthlyInterestRate() == null ? guardada.monthlyInterestRate() : parche.monthlyInterestRate(),
                parche.chargesSingleInstallmentInterest() == null
                        ? guardada.chargesSingleInstallmentInterest()
                        : parche.chargesSingleInstallmentInterest(), parche.isActive() == null ? guardada.active() : parche.isActive(),
                guardada.committedCredit());
    }

    /**
     * Bajar el saldo inicial corre el vigente en la misma diferencia: no puede dejarlo por debajo de cero en una cuenta
     * que no es de credito (FA-133). Va antes del update, asi que no se escribe ningun campo del parche.
     */
    private static void saldoSuficiente(Account guardada, Account resultante) {
        new SaldoNoNegativo(Map.of(guardada.id(), guardada)).exigir(
                Map.of(guardada.id(), resultante.initialBalance().subtract(guardada.initialBalance())),
                "initialBalance");
    }

    /**
     * El alta lo decide con el tipo del cuerpo (CamposDeCredito y SaldoInicialSegunElTipo); el parche no trae tipo,
     * asi que se decide con el guardado. Lanza dentro del flatMap, que lo convierte en senal de error.
     */
    private static void camposDeCreditoSegunElTipo(AccountType tipo, UpdateAccountCommand parche) {
        if (tipo == AccountType.CREDIT) {
            return;
        }
        List<ErrorDetail> errores = new ArrayList<>();
        if (parche.initialBalance() != null && parche.initialBalance().signum() < 0) {
            errores.add(invalido("Solo una cuenta CREDIT puede tener saldo inicial negativo", "initialBalance"));
        }
        if (parche.creditLimit() != null) {
            errores.add(invalido("Solo una cuenta CREDIT tiene cupo", "creditLimit"));
        }
        if (parche.statementDay() != null) {
            errores.add(invalido("Solo una cuenta CREDIT tiene dia de corte", "statementDay"));
        }
        if (parche.paymentDueDay() != null) {
            errores.add(invalido("Solo una cuenta CREDIT tiene dia de pago", "paymentDueDay"));
        }
        if (parche.monthlyInterestRate() != null) {
            errores.add(invalido("Solo una cuenta CREDIT tiene tasa de interes", "monthlyInterestRate"));
        }
        if (parche.chargesSingleInstallmentInterest() != null) {
            errores.add(invalido("Solo una cuenta CREDIT cobra interes en las compras a 1 cuota",
                    "chargesSingleInstallmentInterest"));
        }
        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
        }
    }

    /**
     * Una moneda distinta tiene que estar activa, y la cuenta no puede tener historial: sus
     * movimientos quedarian en una moneda y la cuenta en otra. La misma moneda no es un cambio.
     */
    private Mono<Void> monedaCambiable(Account guardada, String nueva) {
        if (nueva.equals(guardada.currencyCode())) {
            return Mono.empty();
        }
        return monedaActiva(nueva)
                .then(Mono.defer(() -> cuentas.hasTransactions(guardada.id())))
                .filter(tiene -> !tiene)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.CONFLICT,
                        ErrorCodes.RESOURCE_IN_USE,
                        "La cuenta tiene movimientos: su moneda ya no se puede cambiar", "currencyCode")))
                .then();
    }

    /**
     * Una serie activa seguiria creando ocurrencias en una cuenta que ya no admite movimientos (FA-116). Solo
     * se mira al pasar de activa a desactivada: pedir el estado que ya tiene no cambia nada.
     */
    private Mono<Void> sinSeriesSiSeDesactiva(UUID userId, Account guardada, Account resultante) {
        if (!guardada.active() || resultante.active()) {
            return Mono.empty();
        }
        return series.countActiveByAccount(guardada.id(), userId)
                .flatMap(activas -> activas == 0
                        ? Mono.<Void>empty()
                        : Mono.error(new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                                "La cuenta tiene " + seriesActivas(activas)
                                        + ": cancelalas o muevelas a otra cuenta antes de desactivarla",
                                "isActive")));
    }

    /** Los dos juntos: el usuario no descubre el segundo despues de resolver el primero. */
    private static List<ErrorDetail> impedimentos(Account cuenta, long programados, long seriesActivas) {
        List<ErrorDetail> errores = new ArrayList<>();
        if (cuenta.currentBalance().signum() != 0) {
            errores.add(enUso("La cuenta tiene saldo: dejalo en cero antes de borrarla", "currentBalance"));
        }
        if (programados > 0 || seriesActivas > 0) {
            errores.add(enUso(enUsoPor(programados, seriesActivas), "id"));
        }
        return errores;
    }

    /**
     * Un solo error para los dos: toda serie sin fin activa tiene una programada, y dos errores en id dirian
     * lo mismo. Una serie cuya programada se borro a mano bloquea igual (FA-116), o seguiria creando
     * ocurrencias en la cuenta borrada.
     */
    private static String enUsoPor(long programados, long seriesActivas) {
        List<String> partes = new ArrayList<>();
        if (seriesActivas > 0) {
            partes.add(seriesActivas(seriesActivas));
        }
        if (programados > 0) {
            partes.add(programados + (programados == 1 ? " movimiento programado" : " movimientos programados"));
        }
        String solucion = programados > 0
                ? "cancela la serie o la compra en cuotas, o borra esos movimientos, antes de borrarla"
                : "cancela " + (seriesActivas == 1 ? "la serie" : "las series") + " antes de borrarla";
        return "La cuenta tiene " + String.join(" y ", partes) + ": " + solucion;
    }

    /** «1 serie activa», «3 series activas»: lo usan tambien los borrados de cuentas y categorias. */
    static String seriesActivas(long cuantas) {
        return cuantas + (cuantas == 1 ? " serie activa" : " series activas");
    }

    private static ErrorDetail invalido(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(), descripcion, campo);
    }

    private static ErrorDetail enUso(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.RESOURCE_IN_USE.getCode(), descripcion, campo);
    }

    /** Inexistente, borrada y ajena dan lo mismo: distinguirlas confirmaria que el id existe. */
    private static BadRequestException noEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "La cuenta no existe", "id")));
    }
}
