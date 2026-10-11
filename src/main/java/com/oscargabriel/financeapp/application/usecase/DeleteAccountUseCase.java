package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.in.DeleteAccountPort;
import com.oscargabriel.financeapp.domain.port.out.AccountRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class DeleteAccountUseCase implements DeleteAccountPort {

    private final AccountRepositoryPort cuentas;

    /**
     * A diferencia de una categoria, el borrado depende de la fila: una cuenta con saldo sacaria ese
     * dinero, o esa deuda, del listado sin que el usuario lo decida. Por eso lee antes de marcar.
     */
    @Override
    public Mono<Void> delete(UUID userId, UUID accountId) {
        return cuentas.findActiveByIdAndUser(accountId, userId)
                .switchIfEmpty(Mono.error(UpdateAccountUseCase::noEncontrada))
                .flatMap(cuenta -> cuenta.currentBalance().signum() == 0
                        ? cuentas.softDelete(accountId, userId)
                        : Mono.error(new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                                "La cuenta tiene saldo: dejalo en cero antes de borrarla", "currentBalance")))
                .flatMap(borrada -> borrada
                        ? Mono.<Void>empty()
                        : Mono.error(UpdateAccountUseCase.noEncontrada()));
    }
}
