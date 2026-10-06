package com.oscargabriel.financeapp.application.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.UpdateAccountCommand;
import com.oscargabriel.financeapp.domain.port.in.UpdateAccountPort;
import com.oscargabriel.financeapp.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class UpdateAccountUseCase implements UpdateAccountPort {

    private final AccountRepositoryPort cuentas;
    private final CurrencyQueryPort monedas;

    /**
     * El formato del parche ya viene validado por UpdateAccountRequest, incluidos los rangos y que
     * no traiga saldo vigente, tipo ni estado. Aqui queda lo que depende de la cuenta guardada: su
     * tipo, su moneda y sus movimientos. Ningun chequeo escribe; solo el update final.
     */
    @Override
    public Mono<Account> update(UUID userId, UUID accountId, UpdateAccountCommand parche) {
        return cuentas.findActiveByIdAndUser(accountId, userId)
                .switchIfEmpty(Mono.error(UpdateAccountUseCase::noEncontrada))
                .flatMap(guardada -> {
                    camposDeCreditoSegunElTipo(guardada.type(), parche);
                    Account resultante = aplicar(guardada, parche);
                    return monedaCambiable(guardada, resultante.currencyCode())
                            .then(Mono.defer(() -> cuentas.update(userId, resultante)));
                })
                .switchIfEmpty(Mono.error(UpdateAccountUseCase::noEncontrada));
    }

    /**
     * El saldo vigente se copia tal cual: si cambia el inicial, la base lo corre en la misma
     * diferencia (trg_accounts_shift_balance) y el RETURNING del update trae el resultado.
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
                guardada.active());
    }

    /**
     * El alta lo decide con el tipo del cuerpo (CamposDeCredito); el parche no trae tipo, asi que
     * se decide con el guardado. Lanza dentro del flatMap, que lo convierte en senal de error.
     */
    private static void camposDeCreditoSegunElTipo(AccountType tipo, UpdateAccountCommand parche) {
        if (tipo == AccountType.CREDIT) {
            return;
        }
        List<ErrorDetail> errores = new ArrayList<>();
        if (parche.creditLimit() != null) {
            errores.add(invalido("Solo una cuenta CREDIT tiene cupo", "creditLimit"));
        }
        if (parche.statementDay() != null) {
            errores.add(invalido("Solo una cuenta CREDIT tiene dia de corte", "statementDay"));
        }
        if (parche.paymentDueDay() != null) {
            errores.add(invalido("Solo una cuenta CREDIT tiene dia de pago", "paymentDueDay"));
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
        return monedas.exists(nueva)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.BAD_REQUEST,
                        ErrorCodes.VALIDATION_ERROR, "La moneda no existe en el catalogo o no esta activa",
                        "currencyCode")))
                .flatMap(activa -> cuentas.hasTransactions(guardada.id()))
                .filter(tiene -> !tiene)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.CONFLICT,
                        ErrorCodes.RESOURCE_IN_USE,
                        "La cuenta tiene movimientos: su moneda ya no se puede cambiar", "currencyCode")))
                .then();
    }

    private static ErrorDetail invalido(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(), descripcion, campo);
    }

    /** Inexistente, borrada y ajena dan lo mismo: distinguirlas confirmaria que el id existe. */
    static BadRequestException noEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "La cuenta no existe", "id")));
    }
}
