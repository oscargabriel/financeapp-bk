package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.port.in.DeleteTransactionPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class DeleteTransactionUseCase implements DeleteTransactionPort {

    private final TransactionRepositoryPort repositorio;
    private final AccountQueryPort cuentas;
    private final Clock clock;

    /**
     * Lee el movimiento para validar el saldo (FA-133): borrar un ingreso o una transferencia ya gastados no puede dejar
     * una cuenta en negativo. El error va en id, el de la ruta, porque el borrado no tiene cuerpo. El DELETE sigue
     * filtrando por usuario: si la fila desaparecio entre la lectura y la escritura, tambien es 404.
     */
    @Override
    public Mono<Void> delete(UUID userId, UUID transactionId) {
        return repositorio.findByIdAndUser(transactionId, userId)
                .switchIfEmpty(Mono.error(UpdateTransactionUseCase::noEncontrado))
                .flatMap(PendienteDelUsuario::noCompartido)
                .zipWith(cuentas.findByUser(userId, true).collectMap(Account::id))
                .doOnNext(guardadoYCuentas -> new SaldoNoNegativo(guardadoYCuentas.getT2()).exigir(
                        SaldoNoNegativo.diferencia(Map.of(),
                                SaldoNoNegativo.efecto(guardadoYCuentas.getT1(), clock.instant())),
                        "id"))
                .flatMap(validado -> repositorio.deleteByIdAndUser(transactionId, userId))
                .flatMap(borrado -> borrado
                        ? Mono.<Void>empty()
                        : Mono.error(UpdateTransactionUseCase.noEncontrado()));
    }
}
