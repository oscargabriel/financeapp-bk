package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.UuidV7;
import com.oscargabriel.financeapp.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class CreateTransactionsUseCase implements CreateTransactionsPort {

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final TransactionRepositoryPort repositorio;
    private final Clock clock;

    /**
     * Lee una sola vez las cuentas y categorias del usuario, valida el lote entero contra ellas y solo
     * entonces escribe. Leer todas las del usuario, en vez de las del lote, cuesta dos consultas fijas
     * sea cual sea el tamano del lote. El tamano y el formato de cada elemento ya vienen validados por
     * el controlador y CreateTransactionRequest.
     *
     * El reloj se lee una vez por lote: los elementos sin fecha comparten el instante de la peticion.
     */
    @Override
    public Flux<Transaction> create(UUID userId, List<CreateTransactionCommand> lote) {
        return Flux.defer(() -> {
            Mono<Map<UUID, Account>> suyas = cuentas.findByUser(userId, true)
                    .collectMap(Account::id);
            Mono<Map<UUID, Category>> vivas = categorias
                    .findActiveByUser(userId, EnumSet.allOf(CategoryScope.class))
                    .collectMap(Category::id);

            Instant ahora = clock.instant();
            return Mono.zip(suyas, vivas)
                    .map(referencias -> new TransactionBatchValidator(userId, referencias.getT1(),
                            referencias.getT2(), () -> UuidV7.from(clock.instant()), ahora).aMovimientos(lote))
                    .flatMapMany(repositorio::saveAll);
        });
    }
}
