package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.Currency;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.domain.model.UuidV7;
import com.oscargabriel.financeapp.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class CreateTransactionsUseCase implements CreateTransactionsPort {

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final CurrencyQueryPort monedas;
    private final TransactionRepositoryPort repositorio;
    private final ResolveExchangeRatePort tasas;
    private final Clock clock;

    /**
     * Lee una sola vez las cuentas y categorias del usuario, valida el lote entero contra ellas y solo
     * entonces escribe. Leer todas las del usuario, en vez de las del lote, cuesta dos consultas fijas
     * sea cual sea el tamano del lote. El tamano y el formato de cada elemento ya vienen validados por
     * el controlador y CreateTransactionRequest.
     *
     * El reloj se lee una vez por lote: los elementos sin fecha comparten el instante de la peticion.
     *
     * Lo que llego en una moneda distinta a la de su cuenta se convierte con la tasa de su fecha y queda pendiente
     * (FA-51, design.md). Antes de guardar se pide la tasa de hoy de las monedas con elementos de hoy o
     * posteriores (FA-122): el equivalente en USD lo congela la base al insertar, con la ultima tasa guardada.
     */
    @Override
    public Flux<Transaction> create(UUID userId, TransactionOrigin origen, List<CreateTransactionCommand> lote) {
        return Flux.defer(() -> {
            Mono<Map<UUID, Account>> suyas = cuentas.findByUser(userId, true)
                    .collectMap(Account::id);
            Mono<Map<UUID, Category>> vivas = categorias
                    .findActiveByUser(userId, EnumSet.allOf(CategoryScope.class))
                    .collectMap(Category::id);
            Mono<Map<String, Currency>> activas = monedas.findActive().collectMap(Currency::code);

            Instant ahora = clock.instant();
            return Mono.zip(suyas, vivas, activas)
                    .flatMap(referencias -> convertir(new TransactionBatchValidator(userId, referencias.getT1(),
                            referencias.getT2(), referencias.getT3(), () -> UuidV7.from(clock.instant()), ahora,
                            origen).aBorradores(lote), referencias.getT3()))
                    .flatMapMany(movimientos -> tasasDeHoy(movimientos).thenMany(repositorio.saveAll(movimientos)));
        });
    }

    /** En el orden del lote: el lote vuelve en ese orden, y el conversor no pide dos veces la misma tasa. */
    private Mono<List<Transaction>> convertir(List<Borrador> borradores, Map<String, Currency> activas) {
        ConversorDeMontos conversor = new ConversorDeMontos(tasas, activas, clock.getZone());
        return Flux.fromIterable(borradores).concatMap(conversor::convertir).collectList();
    }

    private Mono<Void> tasasDeHoy(List<Transaction> movimientos) {
        LocalDate hoy = LocalDate.now(clock);
        Set<String> monedas = movimientos.stream()
                .filter(t -> !LocalDate.ofInstant(t.occurredAt(), clock.getZone()).isBefore(hoy))
                .map(Transaction::currencyCode)
                .collect(Collectors.toSet());
        return monedas.isEmpty() ? Mono.empty() : tasas.refreshToday(monedas);
    }
}
