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
import com.oscargabriel.financeapp.domain.port.in.DeleteAccountPort;
import com.oscargabriel.financeapp.domain.port.out.AccountRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class DeleteAccountUseCase implements DeleteAccountPort {

    private final AccountRepositoryPort cuentas;

    /**
     * A diferencia de una categoria, el borrado depende de la fila: una cuenta con saldo sacaria ese
     * dinero, o esa deuda, del listado sin que el usuario lo decida. Por eso lee antes de marcar. Lo
     * programado tampoco cuenta en el saldo vigente y se quedaria en la cuenta borrada (FA-115).
     */
    @Override
    public Mono<Void> delete(UUID userId, UUID accountId) {
        return cuentas.findActiveByIdAndUser(accountId, userId)
                .switchIfEmpty(Mono.error(UpdateAccountUseCase::noEncontrada))
                .flatMap(cuenta -> cuentas.countScheduled(accountId)
                        .flatMap(programados -> {
                            List<ErrorDetail> errores = impedimentos(cuenta, programados);
                            return errores.isEmpty()
                                    ? cuentas.softDelete(accountId, userId)
                                    : Mono.error(new BadRequestException(HttpStatus.CONFLICT, errores));
                        }))
                .flatMap(borrada -> borrada
                        ? Mono.<Void>empty()
                        : Mono.error(UpdateAccountUseCase.noEncontrada()));
    }

    /** Los dos juntos: el usuario no descubre el segundo despues de resolver el primero. */
    private static List<ErrorDetail> impedimentos(Account cuenta, long programados) {
        List<ErrorDetail> errores = new ArrayList<>();
        if (cuenta.currentBalance().signum() != 0) {
            errores.add(enUso("La cuenta tiene saldo: dejalo en cero antes de borrarla", "currentBalance"));
        }
        if (programados > 0) {
            errores.add(enUso("La cuenta tiene " + programados
                    + (programados == 1 ? " movimiento programado" : " movimientos programados")
                    + ": cancela la serie o la compra en cuotas, o borra esos movimientos, antes de borrarla",
                    "id"));
        }
        return errores;
    }

    private static ErrorDetail enUso(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.RESOURCE_IN_USE.getCode(), descripcion, campo);
    }
}
