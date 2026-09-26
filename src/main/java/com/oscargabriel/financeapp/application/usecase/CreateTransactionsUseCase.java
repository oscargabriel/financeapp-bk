package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
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
public class CreateTransactionsUseCase implements CreateTransactionsPort {

    /**
     * El tope de FA-26: varios meses de extracto y, con elementos tipicos, bien por debajo del MB
     * del codec. Pasarlo corta el lote antes de mirar sus elementos.
     */
    static final int TOPE_LOTE = 500;

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final TransactionRepositoryPort repositorio;
    private final Clock clock;

    public CreateTransactionsUseCase(AccountQueryPort cuentas, CategoryQueryPort categorias,
            TransactionRepositoryPort repositorio, Clock clock) {
        this.cuentas = cuentas;
        this.categorias = categorias;
        this.repositorio = repositorio;
        this.clock = clock;
    }

    /**
     * Lee una sola vez las cuentas y categorias del usuario, valida el lote entero contra ellas y solo
     * entonces escribe. Leer todas las del usuario, en vez de las del lote, cuesta dos consultas fijas
     * sea cual sea el tamano del lote.
     */
    @Override
    public Flux<Transaction> create(UUID userId, List<CreateTransactionCommand> lote) {
        return Flux.defer(() -> {
            validarTamano(lote);

            Mono<Map<UUID, Account>> suyas = cuentas.findByUser(userId, true)
                    .collectMap(Account::id);
            Mono<Map<UUID, Category>> vivas = categorias
                    .findActiveByUser(userId, EnumSet.allOf(CategoryScope.class))
                    .collectMap(Category::id);

            return Mono.zip(suyas, vivas)
                    .map(referencias -> new TransactionBatchValidator(userId, referencias.getT1(),
                            referencias.getT2(), () -> UuidV7.from(clock.instant())).aMovimientos(lote))
                    .flatMapMany(repositorio::saveAll);
        });
    }

    private static void validarTamano(List<CreateTransactionCommand> lote) {
        if (lote == null || lote.isEmpty() || lote.size() > TOPE_LOTE) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    "El lote debe tener entre 1 y " + TOPE_LOTE + " movimientos", "body");
        }
    }
}
